package com.houseofaje.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.houseofaje.booking.BookingService;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Processes Paystack webhooks. Two rules from the brief are enforced here:
 * <ol>
 *   <li>Verify the HMAC signature of the raw body before reading anything.</li>
 *   <li>Never trust the webhook alone — re-fetch the transaction from the
 *       provider and only mark paid when the provider independently confirms a
 *       successful, amount-matching payment.</li>
 * </ol>
 *
 * <p>Idempotent: a redelivered webhook is acknowledged without double-confirming,
 * because {@code markPaid} only transitions {@code PENDING -> PAID} and only a
 * fresh transition drives {@code booking.confirm}.</p>
 */
@Service
public class PaymentWebhookService {

    private final PaystackWebhookVerifier verifier;
    private final PaymentProvider paymentProvider;
    private final PaymentRepository payments;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookService(
            PaystackWebhookVerifier verifier,
            PaymentProvider paymentProvider,
            PaymentRepository payments,
            BookingService bookingService,
            ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.paymentProvider = paymentProvider;
        this.payments = payments;
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    public void process(String rawBody, String signature) {
        if (!verifier.verify(rawBody, signature)) {
            throw new InvalidWebhookSignatureException();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            return; // not JSON: acknowledge and ignore
        }

        if (!"charge.success".equals(root.path("event").asText())) {
            return; // only act on successful charges
        }

        JsonNode data = root.path("data");
        String transactionReference = data.path("reference").asText();
        String customerCode = data.path("customer").path("customer_code").asText();
        if (transactionReference.isBlank() || customerCode.isBlank()) {
            return;
        }

        Optional<Payment> payment = payments.findPendingByProviderReference(customerCode);
        if (payment.isEmpty()) {
            return; // not one of ours, or already settled
        }

        // Independent server-side verification — do not trust the webhook payload.
        TransactionVerification verification = paymentProvider.verifyTransaction(transactionReference);
        if (!verification.paid() || !matches(payment.get(), verification)) {
            return;
        }

        if (payments.markPaid(payment.get().id(), verification.paidAt(), transactionReference, rawBody)) {
            // A deposit settles the booking; a balance payment only settles the money.
            if (payment.get().type() == PaymentType.DEPOSIT) {
                bookingService.confirm(payment.get().bookingId());
            }
        }
    }

    private boolean matches(Payment payment, TransactionVerification verification) {
        return payment.currency().equals(verification.currency())
                && payment.amount().compareTo(verification.amount()) == 0;
    }
}
