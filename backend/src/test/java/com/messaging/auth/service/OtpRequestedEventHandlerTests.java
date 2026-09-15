package com.messaging.auth.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.messaging.config.JacksonConfig;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.event.OtpRequestedEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.notification.service.NotificationService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OtpRequestedEventHandlerTests {

  @Test
  void handlesOtpRequestedEventBySendingEmail() {
    NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    OtpRequestedEventHandler handler =
        new OtpRequestedEventHandler(new JacksonConfig().objectMapper(), notificationService);
    KafkaEvent<OtpRequestedEvent> event =
        KafkaEvent.of(
            EventMetadata.create(
                OtpRequestedEventHandler.EVENT_TYPE,
                "v1",
                null,
                10L,
                null,
                null,
                MessagePriority.CRITICAL),
            new OtpRequestedEvent(
                "user@example.com", "EMAIL", "Verify your email", "Your code is 123456", false));

    handler.handle(event);

    verify(notificationService)
        .send(
            argThat(
                request ->
                    request.recipient().equals("user@example.com")
                        && request.subject().equals("Verify your email")
                        && request.body().equals("Your code is 123456")
                        && !request.html()));
  }

  @Test
  void handlesOtpRequestedEventWhenKafkaDeserializesPayloadAsMap() {
    NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    OtpRequestedEventHandler handler =
        new OtpRequestedEventHandler(new JacksonConfig().objectMapper(), notificationService);
    KafkaEvent<Map<String, Object>> event =
        KafkaEvent.of(
            EventMetadata.create(
                OtpRequestedEventHandler.EVENT_TYPE,
                "v1",
                null,
                10L,
                null,
                null,
                MessagePriority.CRITICAL),
            Map.of(
                "identifier",
                "user@example.com",
                "deliveryChannel",
                "EMAIL",
                "subject",
                "Verify your email",
                "body",
                "Your code is 123456",
                "html",
                false));

    handler.handle(event);

    verify(notificationService)
        .send(
            argThat(
                request ->
                    request.recipient().equals("user@example.com")
                        && request.subject().equals("Verify your email")
                        && request.body().equals("Your code is 123456")
                        && !request.html()));
  }
}
