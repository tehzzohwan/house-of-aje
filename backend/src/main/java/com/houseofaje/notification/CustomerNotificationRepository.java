package com.houseofaje.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The customer's in-app notification inbox, separate from the outbound delivery log. */
@Repository
public class CustomerNotificationRepository {

    private final JdbcTemplate jdbc;

    public CustomerNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID customerId, UUID bookingId, String type) {
        jdbc.update(
                "INSERT INTO customer_notifications (customer_id, booking_id, type) VALUES (?, ?, ?)",
                customerId, bookingId, type);
    }

    public List<CustomerNotification> findByCustomer(UUID customerId, int limit) {
        return jdbc.query("""
                SELECT n.id, n.type, s.name AS service_name, b.start_at, n.created_at, n.read_at
                FROM customer_notifications n
                LEFT JOIN bookings b ON b.id = n.booking_id
                LEFT JOIN services s ON s.id = b.service_id
                WHERE n.customer_id = ?
                ORDER BY n.created_at DESC
                LIMIT ?
                """,
                (rs, n) -> new CustomerNotification(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("service_name"),
                        rs.getObject("start_at") == null
                                ? null
                                : rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("read_at") == null
                                ? null
                                : rs.getObject("read_at", OffsetDateTime.class).toInstant()),
                customerId, limit);
    }

    public boolean markRead(long id, UUID customerId) {
        return jdbc.update(
                "UPDATE customer_notifications SET read_at = now() WHERE id = ? AND customer_id = ? AND read_at IS NULL",
                id, customerId) == 1;
    }
}
