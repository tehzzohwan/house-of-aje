package com.houseofaje.booking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.houseofaje.booking.BookingStatus.EXPIRED;
import static com.houseofaje.booking.BookingStatus.PENDING_PAYMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    BookingRepository bookings;
    @Mock
    BookingService bookingService;

    private ReconciliationService service() {
        return new ReconciliationService(bookings, bookingService, Duration.ofMinutes(15));
    }

    @Test
    void expiresEachAbandonedBooking() {
        Booking a = booking(UUID.randomUUID());
        Booking b = booking(UUID.randomUUID());
        when(bookings.findPendingPaymentBefore(any(Instant.class))).thenReturn(List.of(a, b));

        service().expireAbandonedBookings();

        verify(bookingService).expire(a.id());
        verify(bookingService).expire(b.id());
    }

    @Test
    void swallowsRaceWhereBookingAlreadyConfirmed() {
        Booking a = booking(UUID.randomUUID());
        when(bookings.findPendingPaymentBefore(any(Instant.class))).thenReturn(List.of(a));
        when(bookingService.expire(a.id()))
                .thenThrow(new InvalidStateTransitionException(PENDING_PAYMENT, EXPIRED));

        service().expireAbandonedBookings();

        verify(bookingService).expire(a.id());
    }

    @Test
    void doesNothingWhenNothingToExpire() {
        when(bookings.findPendingPaymentBefore(any(Instant.class))).thenReturn(List.of());

        service().expireAbandonedBookings();

        verify(bookingService, never()).expire(any());
    }

    private static Booking booking(UUID id) {
        Instant now = Instant.now();
        return new Booking(id, "ref-" + id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                now, now.plusSeconds(3600), PENDING_PAYMENT, BigDecimal.TEN, BigDecimal.ONE, now);
    }
}
