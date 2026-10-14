package com.houseofaje.notification;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The append-only notification delivery log. A row is written for every
 * notification that must be delivered; a downstream sender marks it SENT/FAILED.
 */
@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(NotificationEvent event) {
        jdbc.update("""
                INSERT INTO notifications (booking_id, customer_id, channel, type, recipient)
                VALUES (?, ?, ?, ?, ?)
                """,
                event.bookingId(), event.customerId(), event.channel().name(), event.type().name(), event.recipient());
    }

    public boolean exists(UUID bookingId, NotificationType type) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE booking_id = ? AND type = ?",
                Integer.class, bookingId, type.name());
        return count != null && count > 0;
    }
}
