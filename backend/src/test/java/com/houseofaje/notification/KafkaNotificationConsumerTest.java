package com.houseofaje.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.houseofaje.notification.NotificationChannel.EMAIL;
import static com.houseofaje.notification.NotificationType.CONFIRMATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationConsumerTest {

    @Mock
    NotificationRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void persistsParsedEvent() throws Exception {
        KafkaNotificationConsumer consumer = new KafkaNotificationConsumer(repository, objectMapper);
        NotificationEvent event = new NotificationEvent(CONFIRMATION, UUID.randomUUID(), UUID.randomUUID(), "a@b.com", EMAIL);

        consumer.onMessage(objectMapper.writeValueAsString(event));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(CONFIRMATION);
        assertThat(captor.getValue().recipient()).isEqualTo("a@b.com");
        assertThat(captor.getValue().channel()).isEqualTo(EMAIL);
    }

    @Test
    void dropsMalformedPayload() {
        KafkaNotificationConsumer consumer = new KafkaNotificationConsumer(repository, objectMapper);

        consumer.onMessage("{not-json");

        verify(repository, never()).insert(any());
    }
}
