package com.houseofaje.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes notifications off the topic and records them in the delivery log.
 * Only registered when {@code hoa.notifications.delivery=kafka}.
 */
@Component
@ConditionalOnProperty(name = "hoa.notifications.delivery", havingValue = "kafka")
public class KafkaNotificationConsumer {

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    public KafkaNotificationConsumer(NotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${hoa.notifications.topic:hoa.notifications}")
    public void onMessage(String payload) {
        try {
            repository.insert(objectMapper.readValue(payload, NotificationEvent.class));
        } catch (JsonProcessingException e) {
            // Malformed message: drop rather than poison the consumer group.
        }
    }
}
