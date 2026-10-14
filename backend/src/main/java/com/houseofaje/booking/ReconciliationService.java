package com.houseofaje.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Expires abandoned bookings: a booking left in {@code PENDING_PAYMENT} past the
 * payment window releases its slot and Redis hold by moving to {@code EXPIRED}.
 * Runs on a fixed delay; each pass is idempotent and swallows per-booking races
 * (a payment may confirm between our read and our write).
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final Duration paymentWindow;

    public ReconciliationService(
            BookingRepository bookings,
            BookingService bookingService,
            @Value("${hoa.payment-window:PT3M}") Duration paymentWindow) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.paymentWindow = paymentWindow;
    }

    @Scheduled(fixedDelayString = "${hoa.reconciliation-interval:PT1M}")
    public void expireAbandonedBookings() {
        Instant cutoff = Instant.now().minus(paymentWindow);
        List<Booking> abandoned = bookings.findPendingPaymentBefore(cutoff);
        for (Booking booking : abandoned) {
            try {
                bookingService.expire(booking.id());
            } catch (InvalidStateTransitionException | BookingNotFoundException e) {
                // Lost a race with a concurrent confirmation; nothing to do.
                log.debug("Skipping expiry of {}: {}", booking.id(), e.getMessage());
            }
        }
    }
}
