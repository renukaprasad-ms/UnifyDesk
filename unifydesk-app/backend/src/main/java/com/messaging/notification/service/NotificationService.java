package com.messaging.notification.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.sender.NotificationSenderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationSenderFactory senderFactory;

  public void send(SendNotificationRequest request) {
    validate(request);
    // Critical OTP/security sends must complete before their Kafka event is acknowledged.
    // In-memory async dispatch could silently lose failures after the event was consumed.
    senderFactory.get(request.channel()).send(request);
  }

  private void validate(SendNotificationRequest request) {
    if (request == null || request.channel() == null) {
      throw new BadRequestException("Notification channel is required");
    }
    if (!StringUtils.hasText(request.recipient())) {
      throw new BadRequestException("Notification recipient is required");
    }
    if (!StringUtils.hasText(request.subject())) {
      throw new BadRequestException("Notification subject is required");
    }
    if (!StringUtils.hasText(request.body())) {
      throw new BadRequestException("Notification body is required");
    }
  }
}
