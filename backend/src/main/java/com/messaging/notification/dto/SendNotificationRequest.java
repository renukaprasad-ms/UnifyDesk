package com.messaging.notification.dto;

import com.messaging.notification.model.NotificationChannel;

public record SendNotificationRequest(
    NotificationChannel channel, String recipient, String subject, String body, boolean html) {

  public static SendNotificationRequest email(
      String recipient, String subject, String body, boolean html) {
    return new SendNotificationRequest(NotificationChannel.EMAIL, recipient, subject, body, html);
  }
}
