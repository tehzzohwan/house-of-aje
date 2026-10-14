package com.houseofaje.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default transport: write straight to the delivery log. Active unless
 * {@code hoa.notifications.delivery=kafka}.
 */
@Component
@ConditionalOnProperty(name = "hoa.notifications.delivery", havingValue = "direct", matchIfMissing = true)
public class DirectNotificationPublisher implements NotificationPublisher {

    private final NotificationRepository repository;

    public DirectNotificationPublisher(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void publish(NotificationEvent event) {
        repository.insert(event);
    }
}
