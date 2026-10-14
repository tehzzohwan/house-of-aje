package com.houseofaje.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.houseofaje.booking.BookingService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    private static final String SECRET = "sk_test_secret";
    private static final String CHARGE_SUCCESS = """
            {
              "event": "charge.success",
              "data": {
                "reference": "txn_ref_123",
                "status": "success",
                "amount": 400000,
                "currency": "NGN",
                "paid_at": "2026-01-12T09:00:00.000Z",
                "customer": { "customer_code": "CUS_abc" }
              }
            }""";

    @Mock
    PaymentProvider paymentProvider;
    @Mock
    PaymentRepository payments;
    @Mock
    BookingService bookingService;

    private final PaystackWebhookVerifier verifier = new PaystackWebhookVerifier(SECRET);
    private PaymentWebhookService service;

    @BeforeEach
    void setup() {
        service = new PaymentWebhookService(verifier, paymentProvider, payments, bookingService, new ObjectMapper());
    }

    @Test
    void rejectsInvalidSignatureBeforeAnythingElse() {
        assertThatThrownBy(() -> service.process(CHARGE_SUCCESS, "deadbeef"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
        verifyNoInteractions(paymentProvider, payments, bookingService);
    }

    @Test
    void marksPaidAndConfirmsWhenProviderIndependentlyConfirms() {
        Payment payment = pendingPayment("CUS_abc");
        when(payments.findByProviderReference("CUS_abc")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyTransaction("txn_ref_123")).thenReturn(
                new TransactionVerification(true, "txn_ref_123", new BigDecimal("4000.00"), "NGN", "CUS_abc",
                        Instant.parse("2026-01-12T09:00:00Z")));
        when(payments.markPaid(eq(payment.id()), any(), anyString(), anyString())).thenReturn(true);

        service.process(CHARGE_SUCCESS, sign(CHARGE_SUCCESS));

        verify(payments).markPaid(eq(payment.id()), any(), anyString(), anyString());
        verify(bookingService).confirm(payment.bookingId());
    }

    @Test
    void doesNotConfirmWhenProviderSaysNotPaid() {
        Payment payment = pendingPayment("CUS_abc");
        when(payments.findByProviderReference("CUS_abc")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyTransaction("txn_ref_123")).thenReturn(
                new TransactionVerification(false, "txn_ref_123", new BigDecimal("4000.00"), "NGN", "CUS_abc", null));

        service.process(CHARGE_SUCCESS, sign(CHARGE_SUCCESS));

        verify(payments, never()).markPaid(any(), any(), anyString(), anyString());
        verify(bookingService, never()).confirm(any());
    }

    @Test
    void doesNotConfirmWhenAmountMismatches() {
        Payment payment = pendingPayment("CUS_abc");
        when(payments.findByProviderReference("CUS_abc")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyTransaction("txn_ref_123")).thenReturn(
                new TransactionVerification(true, "txn_ref_123", new BigDecimal("3999.00"), "NGN", "CUS_abc", null));

        service.process(CHARGE_SUCCESS, sign(CHARGE_SUCCESS));

        verify(payments, never()).markPaid(any(), any(), anyString(), anyString());
        verify(bookingService, never()).confirm(any());
    }

    @Test
    void ignoresUnknownCustomerCodeWithoutCallingProvider() {
        when(payments.findByProviderReference("CUS_abc")).thenReturn(Optional.empty());

        service.process(CHARGE_SUCCESS, sign(CHARGE_SUCCESS));

        verify(paymentProvider, never()).verifyTransaction(anyString());
        verify(bookingService, never()).confirm(any());
    }

    @Test
    void ignoresNonChargeEvents() {
        String body = "{\"event\":\"customer.created\",\"data\":{}}";

        service.process(body, sign(body));

        verifyNoInteractions(paymentProvider, payments, bookingService);
    }

    @Test
    void doesNotConfirmTwiceWhenAlreadyPaid() {
        Payment payment = pendingPayment("CUS_abc");
        when(payments.findByProviderReference("CUS_abc")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyTransaction("txn_ref_123")).thenReturn(
                new TransactionVerification(true, "txn_ref_123", new BigDecimal("4000.00"), "NGN", "CUS_abc",
                        Instant.parse("2026-01-12T09:00:00Z")));
        when(payments.markPaid(eq(payment.id()), any(), anyString(), anyString())).thenReturn(false); // already PAID

        service.process(CHARGE_SUCCESS, sign(CHARGE_SUCCESS));

        verify(bookingService, never()).confirm(any());
    }

    private static Payment pendingPayment(String customerCode) {
        return new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("4000.00"),
                PaymentType.DEPOSIT,
                PaymentStatus.PENDING,
                "PAYSTACK",
                customerCode,
                null,
                "idem-1",
                "NGN",
                null);
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
