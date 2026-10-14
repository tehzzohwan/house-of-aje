package com.houseofaje.booking;

import com.houseofaje.notification.NotificationRepository;
import com.houseofaje.notification.NotificationService;
import com.houseofaje.notification.NotificationType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.houseofaje.booking.BookingStatus.CONFIRMED;
import static com.houseofaje.notification.NotificationType.REMINDER_1H;
import static com.houseofaje.notification.NotificationType.REMINDER_24H;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-12T09:00:00Z");

    @Mock
    BookingRepository bookings;
    @Mock
    NotificationRepository notifications;
    @Mock
    NotificationService notificationService;

    private ReminderService service() {
        return new ReminderService(bookings, notifications, notificationService, Duration.ofHours(1));
    }

    @Test
    void emitsTwentyFourHourReminderWhenDue() {
        Booking booking = booking(NOW.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(30)));
        when(bookings.findConfirmedStartingBetween(
                eq(NOW.plus(Duration.ofHours(23))), eq(NOW.plus(Duration.ofHours(24)))))
                .thenReturn(List.of(booking));
        when(notifications.exists(booking.id(), REMINDER_24H)).thenReturn(false);
        when(bookings.findConfirmedStartingBetween(
                eq(NOW), eq(NOW.plus(Duration.ofHours(1)))))
                .thenReturn(List.of());

        service().sendReminders(NOW);

        verify(notificationService).notifyBooking(booking, REMINDER_24H);
    }

    @Test
    void skipsReminderAlreadySent() {
        Booking booking = booking(NOW.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(30)));
        when(bookings.findConfirmedStartingBetween(
                eq(NOW.plus(Duration.ofHours(23))), eq(NOW.plus(Duration.ofHours(24)))))
                .thenReturn(List.of(booking));
        when(notifications.exists(booking.id(), REMINDER_24H)).thenReturn(true);
        when(bookings.findConfirmedStartingBetween(
                eq(NOW), eq(NOW.plus(Duration.ofHours(1)))))
                .thenReturn(List.of());

        service().sendReminders(NOW);

        verify(notificationService, never()).notifyBooking(booking, REMINDER_24H);
    }

    @Test
    void emitsOneHourReminderWhenDue() {
        Booking booking = booking(NOW.plus(Duration.ofMinutes(45)));
        when(bookings.findConfirmedStartingBetween(
                eq(NOW.plus(Duration.ofHours(23))), eq(NOW.plus(Duration.ofHours(24)))))
                .thenReturn(List.of());
        when(bookings.findConfirmedStartingBetween(
                eq(NOW), eq(NOW.plus(Duration.ofHours(1)))))
                .thenReturn(List.of(booking));
        when(notifications.exists(booking.id(), REMINDER_1H)).thenReturn(false);

        service().sendReminders(NOW);

        verify(notificationService).notifyBooking(booking, REMINDER_1H);
    }

    @Test
    void doesNothingWhenNothingDue() {
        when(bookings.findConfirmedStartingBetween(
                eq(NOW.plus(Duration.ofHours(23))), eq(NOW.plus(Duration.ofHours(24)))))
                .thenReturn(List.of());
        when(bookings.findConfirmedStartingBetween(
                eq(NOW), eq(NOW.plus(Duration.ofHours(1)))))
                .thenReturn(List.of());

        service().sendReminders(NOW);

        verify(notificationService, never()).notifyBooking(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(NotificationType.class));
    }

    private static Booking booking(Instant start) {
        return new Booking(UUID.randomUUID(), "ref-" + start, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                start, start.plusSeconds(3600), CONFIRMED, BigDecimal.TEN, BigDecimal.ONE, Instant.now());
    }
}
