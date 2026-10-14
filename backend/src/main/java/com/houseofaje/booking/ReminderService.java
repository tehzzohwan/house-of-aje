package com.houseofaje.booking;

import com.houseofaje.notification.NotificationRepository;
import com.houseofaje.notification.NotificationService;
import com.houseofaje.notification.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends appointment reminders. Each sweep looks for confirmed bookings starting
 * shortly before the 24h and 1h marks and, using the delivery log to dedupe,
 * emits one reminder per booking per mark.
 */
@Service
public class ReminderService {

    private final BookingRepository bookings;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final Duration reminderInterval;

    public ReminderService(
            BookingRepository bookings,
            NotificationRepository notifications,
            NotificationService notificationService,
            @Value("${hoa.reminder-interval:PT1H}") Duration reminderInterval) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.reminderInterval = reminderInterval;
    }

    @Scheduled(fixedDelayString = "${hoa.reminder-interval:PT1H}")
    public void sendReminders() {
        sendReminders(Instant.now());
    }

    void sendReminders(Instant now) {
        sendFor(now.plus(Duration.ofHours(24)), NotificationType.REMINDER_24H);
        sendFor(now.plus(Duration.ofHours(1)), NotificationType.REMINDER_1H);
        sendSessionStarted(now);
    }

    private void sendFor(Instant target, NotificationType type) {
        List<Booking> due = bookings.findConfirmedStartingBetween(target.minus(reminderInterval), target);
        for (Booking booking : due) {
            if (!notifications.exists(booking.id(), type)) {
                notificationService.notifyBooking(booking, type);
            }
        }
    }

    /** Confirmed bookings whose start time just elapsed: tell the customer their session began. */
    private void sendSessionStarted(Instant now) {
        List<Booking> started = bookings.findConfirmedStartingBetween(now.minus(reminderInterval), now);
        for (Booking booking : started) {
            if (!notifications.exists(booking.id(), NotificationType.SESSION_STARTED)) {
                notificationService.notifyBooking(booking, NotificationType.SESSION_STARTED);
            }
        }
    }
}
