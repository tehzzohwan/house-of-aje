package com.houseofaje.payment;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Paystack bank-transfer implementation: a dedicated virtual account per
 * booking's customer. Amounts on the wire are in kobo; this adapter converts to
 * NGN so the rest of the system deals only in naira.
 *
 * <p>{@code verifyTransaction} is safe to retry (idempotent GET);
 * {@code initiateDeposit} is only circuit-broken, never auto-retried, because a
 * POST that may have already been applied server-side must not be replayed.</p>
 */
@Component
@ConditionalOnProperty(name = "hoa.payment.provider", havingValue = "paystack")
public class PaystackPaymentProvider implements PaymentProvider {

    private final RestClient client;
    private final String preferredBank;

    public PaystackPaymentProvider(
            RestClient.Builder builder,
            @Value("${hoa.paystack.base-url:https://api.paystack.co}") String baseUrl,
            @Value("${hoa.paystack.secret-key}") String secretKey,
            @Value("${hoa.paystack.preferred-bank:wema-bank}") String preferredBank) {
        this.preferredBank = preferredBank;
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String name() {
        return "PAYSTACK";
    }

    @Override
    @CircuitBreaker(name = "paystack")
    public DepositInstructions initiateDeposit(DepositRequest request) {
        Map<String, Object> body = Map.of(
                "email", request.customerEmail(),
                "phone", request.customerPhone() == null ? "" : request.customerPhone(),
                "preferred_bank", preferredBank,
                "country", "NG");
        JsonNode data = client.post().uri("/dedicated_account")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class)
                .path("data");

        // provider_reference carries the customer_code: the stable key the
        // charge.success webhook supplies to map a transfer back to our payment.
        return new DepositInstructions(
                data.path("customer").path("customer_code").asText(),
                data.path("account_number").asText(),
                data.path("account_name").asText(),
                data.path("bank").path("name").asText());
    }

    @Override
    @CircuitBreaker(name = "paystack")
    @Retry(name = "paystack")
    public TransactionVerification verifyTransaction(String transactionReference) {
        JsonNode data = client.get().uri("/transaction/verify/{reference}", transactionReference)
                .retrieve()
                .body(JsonNode.class)
                .path("data");

        boolean paid = "success".equals(data.path("status").asText());
        // Paystack reports amounts in kobo; move the decimal two places to naira.
        BigDecimal amount = BigDecimal.valueOf(data.path("amount").asLong()).movePointLeft(2);
        return new TransactionVerification(
                paid,
                data.path("reference").asText(),
                amount,
                data.path("currency").asText(),
                data.path("customer").path("customer_code").asText(),
                parseInstant(data.path("paid_at").asText(null)));
    }

    @Override
    @CircuitBreaker(name = "paystack")
    public RefundResult refund(RefundRequest request) {
        long kobo = request.amount().movePointRight(2).longValueExact();
        JsonNode data = client.post().uri("/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("transaction", request.transactionReference(), "amount", kobo))
                .retrieve()
                .body(JsonNode.class)
                .path("data");
        return new RefundResult(!"failed".equals(data.path("status").asText()),
                data.path("reference").asText());
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
