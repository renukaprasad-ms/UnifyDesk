package com.messaging.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.BadRequestException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.model.NotificationChannel;
import com.messaging.notification.sender.NotificationSender;
import com.messaging.notification.sender.NotificationSenderFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTests {

  @Test
  void sendRoutesToChannelStrategy() {
    NotificationSender sender = org.mockito.Mockito.mock(NotificationSender.class);
    when(sender.channel()).thenReturn(NotificationChannel.EMAIL);
    NotificationProperties properties = new NotificationProperties();
    properties.setAsync(false);
    SendNotificationRequest request =
        SendNotificationRequest.email("user@example.com", "Subject", "Body", false);

    NotificationService service =
        new NotificationService(
            new NotificationSenderFactory(List.of(sender)), properties, Runnable::run);

    service.send(request);

    verify(sender).send(request);
  }

  @Test
  void sendRejectsMissingRecipient() {
    NotificationService service =
        new NotificationService(
            new NotificationSenderFactory(List.of()), new NotificationProperties(), Runnable::run);

    assertThatThrownBy(
            () ->
                service.send(
                    new SendNotificationRequest(NotificationChannel.EMAIL, " ", "Subject", "Body", false)))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Notification recipient is required");
  }
}
