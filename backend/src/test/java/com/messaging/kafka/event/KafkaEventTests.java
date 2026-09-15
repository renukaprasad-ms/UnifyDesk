package com.messaging.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.config.JacksonConfig;
import com.messaging.kafka.priority.MessagePriority;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

class KafkaEventTests {

  @Test
  void createsEnvelopeWithRequiredMetadata() {
    EventMetadata metadata =
        EventMetadata.create(
            "auth.otp.requested",
            "v1",
            10L,
            20L,
            null,
            null,
            MessagePriority.CRITICAL);

    KafkaEvent<OtpRequestedEvent> event =
        KafkaEvent.of(
            metadata,
            new OtpRequestedEvent(
                "user@example.com", "EMAIL", "Verify your email", "Your code is 123456", false));

    assertThat(event.eventId()).isNotBlank();
    assertThat(event.eventType()).isEqualTo("auth.otp.requested");
    assertThat(event.eventVersion()).isEqualTo("v1");
    assertThat(event.occurredAt()).isNotNull();
    assertThat(event.priority()).isEqualTo(MessagePriority.CRITICAL);
    assertThat(event.correlationId()).isNotBlank();
    assertThat(event.payload())
        .isEqualTo(
            new OtpRequestedEvent(
                "user@example.com", "EMAIL", "Verify your email", "Your code is 123456", false));
  }

  @Test
  void serializesWithJavaTimeObjectMapperForKafka() {
    ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    JsonSerializer<KafkaEvent<?>> serializer = new JsonSerializer<>(objectMapper);
    serializer.setAddTypeInfo(false);
    KafkaEvent<OtpRequestedEvent> event =
        KafkaEvent.of(
            EventMetadata.create(
                "auth.otp.requested",
                "v1",
                null,
                null,
                "corr",
                null,
                MessagePriority.CRITICAL),
            new OtpRequestedEvent(
                "user@example.com", "EMAIL", "Verify your email", "Your code is 123456", false));

    byte[] serialized = serializer.serialize("messaging.jobs.critical", event);

    assertThat(serialized).isNotEmpty();
    assertThat(new String(serialized, java.nio.charset.StandardCharsets.UTF_8))
        .contains("\"occurredAt\"");
  }
}
