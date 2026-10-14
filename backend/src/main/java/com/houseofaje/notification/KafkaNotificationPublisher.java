package com.houseofaje.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka transport, active when {@code hoa.notifications.delivery=kafka}. The
 * event is serialised to JSON and published keyed by booking id so ordering is
 * stable per booking.
 */
@Component
@ConditionalOnProperty(name = "hoa.notifications.delivery", havingValue = "kafka")
public class KafkaNotificationPublisher implements NotificationPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaNotificationPublisher(
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper,
            @Value("${hoa.notifications.topic:hoa.notifications}") String topic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(NotificationEvent event) {
        try {
            String key = event.bookingId() == null ? "system" : event.bookingId().toString();
            kafka.send(topic, key, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification", e);
        }
    }
}
