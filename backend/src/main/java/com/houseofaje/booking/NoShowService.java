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
 * Auto-marks confirmed bookings as {@code NO_SHOW} once their scheduled end time
 * has passed by a grace window without staff completing or cancelling them.
 * Admin can still override the status manually from the console.
 */
@Service
public class NoShowService {

    private static final Logger log = LoggerFactory.getLogger(NoShowService.class);

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final Duration graceWindow;

    public NoShowService(
            BookingRepository bookings,
            BookingService bookingService,
            @Value("${hoa.no-show-grace-window:PT30M}") Duration graceWindow) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.graceWindow = graceWindow;
    }

    @Scheduled(fixedDelayString = "${hoa.no-show-interval:PT5M}")
    public void markNoShows() {
        Instant cutoff = Instant.now().minus(graceWindow);
        List<Booking> missed = bookings.findConfirmedEndedBefore(cutoff);
        for (Booking booking : missed) {
            try {
                bookingService.noShow(booking.id());
            } catch (InvalidStateTransitionException | BookingNotFoundException e) {
                // Lost a race with a concurrent transition; nothing to do.
                log.debug("Skipping no-show mark of {}: {}", booking.id(), e.getMessage());
            }
        }
    }
}
