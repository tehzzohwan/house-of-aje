package com.houseofaje.notification;

import com.houseofaje.booking.Booking;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Turns a booking lifecycle event into a notification for that customer,
 * choosing a channel: email when the customer has one, otherwise SMS.
 */
@Service
public class NotificationService {

    private final NotificationPublisher publisher;
    private final JdbcTemplate jdbc;
    private final CustomerNotificationRepository inApp;

    public NotificationService(NotificationPublisher publisher, JdbcTemplate jdbc,
            CustomerNotificationRepository inApp) {
        this.publisher = publisher;
        this.jdbc = jdbc;
        this.inApp = inApp;
    }

    public void notifyBooking(Booking booking, NotificationType type) {
        Customer customer = customer(booking.customerId());
        if (customer.email() != null && !customer.email().isBlank()) {
            publisher.publish(new NotificationEvent(type, booking.id(), booking.customerId(),
                    customer.email(), NotificationChannel.EMAIL));
        } else {
            publisher.publish(new NotificationEvent(type, booking.id(), booking.customerId(),
                    customer.phone(), NotificationChannel.SMS));
        }
        inApp.insert(booking.customerId(), booking.id(), type.name());
    }

    private Customer customer(UUID customerId) {
        List<Customer> rows = jdbc.query(
                "SELECT email, phone FROM customers WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> new Customer(rs.getString("email"), rs.getString("phone")),
                customerId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        return rows.get(0);
    }

    private record Customer(String email, String phone) {}
}
