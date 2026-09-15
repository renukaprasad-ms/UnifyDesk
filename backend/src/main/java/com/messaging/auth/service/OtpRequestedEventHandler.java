package com.messaging.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.event.OtpRequestedEvent;
import com.messaging.kafka.exception.NonRetryableKafkaException;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpRequestedEventHandler implements KafkaEventHandler {

  public static final String EVENT_TYPE = "auth.otp.requested";

  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  @Override
  public boolean supports(String eventType) {
    return EVENT_TYPE.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    long startedAt = System.nanoTime();
    OtpRequestedEvent payload = payload(event.payload());
    if (!"EMAIL".equalsIgnoreCase(payload.deliveryChannel())) {
      throw new NonRetryableKafkaException("Unsupported OTP delivery channel");
    }

    long sendStartedAt = System.nanoTime();
    notificationService.send(
        SendNotificationRequest.email(
            payload.identifier(), payload.subject(), payload.body(), payload.html()));
    log.info(
        "OTP event handled eventId={} recipient={} notificationSubmitDurationMs={} totalDurationMs={}",
        event.eventId(),
        payload.identifier(),
        elapsedMs(sendStartedAt),
        elapsedMs(startedAt));
  }

  private OtpRequestedEvent payload(Object payload) {
    if (payload instanceof OtpRequestedEvent otpRequestedEvent) {
      return otpRequestedEvent;
    }
    try {
      return objectMapper.convertValue(payload, OtpRequestedEvent.class);
    } catch (IllegalArgumentException exception) {
      throw new NonRetryableKafkaException("Invalid OTP requested payload", exception);
    }
  }

  private long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}
