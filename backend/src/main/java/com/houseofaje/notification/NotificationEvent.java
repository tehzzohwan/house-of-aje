package com.houseofaje.notification;

import java.util.UUID;

/** A notification to deliver to a customer (the message on the pipeline). */
public record NotificationEvent(
        NotificationType type,
        UUID bookingId,
        UUID customerId,
        String recipient,
        NotificationChannel channel) {}
