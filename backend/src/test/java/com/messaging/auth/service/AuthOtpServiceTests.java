package com.messaging.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.event.OtpRequestedEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import com.messaging.redis.config.RedisTtlProperties;
import com.messaging.redis.service.RateLimitResult;
import com.messaging.redis.service.RedisRateLimitService;
import com.messaging.redis.service.RedisService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthOtpServiceTests {

  @Test
  void issueEmailVerificationOtpStoresOtpAndPublishesCriticalKafkaEvent() {
    RedisService redisService = org.mockito.Mockito.mock(RedisService.class);
    RedisRateLimitService rateLimitService = org.mockito.Mockito.mock(RedisRateLimitService.class);
    EventPublisher eventPublisher = org.mockito.Mockito.mock(EventPublisher.class);
    RedisTtlProperties ttlProperties = new RedisTtlProperties();
    when(rateLimitService.consume(any(), any(Integer.class), any(Duration.class)))
        .thenReturn(new RateLimitResult(true, 1, 5, 4, Duration.ZERO));

    AuthOtpService service =
        new AuthOtpService(redisService, rateLimitService, ttlProperties, eventPublisher);

    service.issueEmailVerificationOtp("USER@example.com");

    verify(redisService).set(any(), any(), any(Duration.class));
    ArgumentCaptor<KafkaEvent<OtpRequestedEvent>> eventCaptor =
        ArgumentCaptor.forClass(KafkaEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());
    KafkaEvent<OtpRequestedEvent> event = eventCaptor.getValue();
    assertThat(event.eventType()).isEqualTo(OtpRequestedEventHandler.EVENT_TYPE);
    assertThat(event.priority()).isEqualTo(MessagePriority.CRITICAL);
    assertThat(event.payload().identifier()).isEqualTo("user@example.com");
    assertThat(event.payload().deliveryChannel()).isEqualTo("EMAIL");
    assertThat(event.payload().body()).contains("Your verification code is");
  }
}
