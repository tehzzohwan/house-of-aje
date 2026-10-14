package com.houseofaje.payment;

import com.houseofaje.booking.AuditLogRepository;
import com.houseofaje.booking.Booking;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cancellation refunds. Per the product rules: refund 30% of the service price
 * (the other 10% of the 40% deposit is retained as the breaking fee). A refund
 * never blocks the cancellation — failures are recorded so they can be retried
 * by reconciliation.
 */
@Service
public class RefundService {

    private static final BigDecimal REFUND_RATIO = new BigDecimal("0.30");

    private final PaymentRepository payments;
    private final PaymentProvider paymentProvider;
    private final AuditLogRepository auditLog;

    public RefundService(PaymentRepository payments, PaymentProvider paymentProvider, AuditLogRepository auditLog) {
        this.payments = payments;
        this.paymentProvider = paymentProvider;
        this.auditLog = auditLog;
    }

    public void refundDeposit(Booking booking) {
        Payment deposit = payments.findDepositForBooking(booking.id()).orElse(null);
        if (deposit == null || deposit.status() != PaymentStatus.PAID) {
            return; // nothing paid, nothing to refund
        }
        BigDecimal refundAmount = booking.price().multiply(REFUND_RATIO).setScale(2, RoundingMode.HALF_UP);
        if (refundAmount.signum() <= 0) {
            return;
        }

        Payment refundPayment = new Payment(UUID.randomUUID(), booking.id(), refundAmount, PaymentType.REFUND,
                PaymentStatus.PENDING, paymentProvider.name(), null, null,
                booking.reference() + "-refund", deposit.currency(), null);
        payments.insert(refundPayment);

        // Cash/manual deposits have no provider transaction to reverse, so the
        // refund is settled immediately. Provider-backed deposits go through the
        // normal refund call below.
        if (deposit.providerTransactionReference() == null) {
            payments.updateStatus(refundPayment.id(), PaymentStatus.PAID, Instant.now());
            auditLog.log("payment", refundPayment.id().toString(), "REFUND", null,
                    Map.of("amount", refundAmount),
                    Map.of("status", PaymentStatus.PAID.name()));
            return;
        }

        RefundResult result = refund(deposit, refundAmount);
        if (result.success()) {
            payments.updateStatus(refundPayment.id(), PaymentStatus.PAID, Instant.now());
        } else {
            payments.updateStatus(refundPayment.id(), PaymentStatus.FAILED);
        }
        auditLog.log("payment", refundPayment.id().toString(), "REFUND", null,
                Map.of("amount", refundAmount),
                Map.of("status", result.success() ? PaymentStatus.PAID.name() : PaymentStatus.FAILED.name()));
    }

    private RefundResult refund(Payment deposit, BigDecimal amount) {
        if (deposit.providerTransactionReference() == null) {
            return new RefundResult(false, null); // cannot refund without the source transaction
        }
        try {
            return paymentProvider.refund(new RefundRequest(
                    deposit.providerTransactionReference(), amount, deposit.currency()));
        } catch (Exception e) {
            return new RefundResult(false, null);
        }
    }
}
