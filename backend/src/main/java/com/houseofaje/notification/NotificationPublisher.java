package com.houseofaje.notification;

/** Emits a notification. Implementations differ only in transport. */
public interface NotificationPublisher {

    void publish(NotificationEvent event);
}
