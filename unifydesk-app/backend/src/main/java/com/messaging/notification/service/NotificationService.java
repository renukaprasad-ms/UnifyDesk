package com.messaging.notification.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.sender.NotificationSender;
import com.messaging.notification.sender.NotificationSenderFactory;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationSenderFactory senderFactory;
  private final NotificationProperties notificationProperties;
  private final Executor notificationExecutor;

  public void send(SendNotificationRequest request) {
    validate(request);
    NotificationSender sender = senderFactory.get(request.channel());

    if (!notificationProperties.isAsync()) {
      sender.send(request);
      return;
    }

    notificationExecutor.execute(
        () -> {
          try {
            sender.send(request);
          } catch (RuntimeException exception) {
            // Async notification failures are intentionally isolated from the caller.
          }
        });
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
