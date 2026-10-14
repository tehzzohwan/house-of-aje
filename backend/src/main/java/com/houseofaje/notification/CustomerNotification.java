package com.houseofaje.notification;

import java.time.Instant;

/** An in-app notification shown in the customer's account. */
public record CustomerNotification(
        long id,
        String type,
        String serviceName,
        Instant startAt,
        Instant createdAt,
        Instant readAt) {}
