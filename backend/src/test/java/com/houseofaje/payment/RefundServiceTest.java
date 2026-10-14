package com.houseofaje.payment;

import com.houseofaje.booking.AuditLogRepository;
import com.houseofaje.booking.Booking;
import com.houseofaje.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    PaymentRepository payments;
    @Mock
    PaymentProvider paymentProvider;
    @Mock
    AuditLogRepository auditLog;

    @Test
    void refundsThirtyPercentOfPriceForPaidDeposit() {
        Booking booking = booking("10000.00");
        Payment deposit = deposit(PaymentStatus.PAID, "txn_x");
        when(payments.findDepositForBooking(booking.id())).thenReturn(Optional.of(deposit));
        when(paymentProvider.name()).thenReturn("PAYSTACK");
        when(paymentProvider.refund(any())).thenReturn(new RefundResult(true, "ref_1"));

        new RefundService(payments, paymentProvider, auditLog).refundDeposit(booking);

        ArgumentCaptor<Payment> inserted = ArgumentCaptor.forClass(Payment.class);
        verify(payments).insert(inserted.capture());
        assertThat(inserted.getValue().amount()).isEqualByComparingTo("3000.00");
        assertThat(inserted.getValue().type()).isEqualTo(PaymentType.REFUND);
        assertThat(inserted.getValue().idempotencyKey()).isEqualTo(booking.reference() + "-refund");

        ArgumentCaptor<RefundRequest> request = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentProvider).refund(request.capture());
        assertThat(request.getValue().transactionReference()).isEqualTo("txn_x");
        assertThat(request.getValue().amount()).isEqualByComparingTo("3000.00");

        verify(payments).updateStatus(eq(inserted.getValue().id()), eq(PaymentStatus.PAID), any(Instant.class));
    }

    @Test
    void doesNothingWhenDepositNotPaid() {
        Booking booking = booking("10000.00");
        when(payments.findDepositForBooking(booking.id()))
                .thenReturn(Optional.of(deposit(PaymentStatus.PENDING, "txn_x")));

        new RefundService(payments, paymentProvider, auditLog).refundDeposit(booking);

        verify(payments, never()).insert(any());
        verify(paymentProvider, never()).refund(any());
    }

    @Test
    void marksRefundFailedWhenNoTransactionReference() {
        Booking booking = booking("10000.00");
        when(payments.findDepositForBooking(booking.id()))
                .thenReturn(Optional.of(deposit(PaymentStatus.PAID, null)));
        when(paymentProvider.name()).thenReturn("PAYSTACK");

        new RefundService(payments, paymentProvider, auditLog).refundDeposit(booking);

        ArgumentCaptor<Payment> inserted = ArgumentCaptor.forClass(Payment.class);
        verify(payments).insert(inserted.capture());
        verify(paymentProvider, never()).refund(any());
        verify(payments).updateStatus(inserted.getValue().id(), PaymentStatus.FAILED);
    }

    private static Booking booking(String price) {
        BigDecimal p = new BigDecimal(price);
        return new Booking(UUID.randomUUID(), "ref123", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), Instant.now(), BookingStatus.CONFIRMED, p,
                p.multiply(new BigDecimal("0.40")), Instant.now());
    }

    private static Payment deposit(PaymentStatus status, String txnReference) {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4000.00"), PaymentType.DEPOSIT,
                status, "PAYSTACK", "CUS_x", txnReference, "idem-1", "NGN", null);
    }
}
