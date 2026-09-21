package com.messaging.notification.dto;

import com.messaging.notification.model.NotificationChannel;

public record SendNotificationRequest(
    NotificationChannel channel,
    String recipient,
    String subject,
    String body,
    boolean html,
    String textBody,
    String htmlBody) {

  public SendNotificationRequest(
      NotificationChannel channel,
      String recipient,
      String subject,
      String body,
      boolean html) {
    this(channel, recipient, subject, body, html, null, null);
  }

  public static SendNotificationRequest email(
      String recipient, String subject, String body, boolean html) {
    return new SendNotificationRequest(
        NotificationChannel.EMAIL, recipient, subject, body, html, null, null);
  }

  public static SendNotificationRequest emailMultipart(
      String recipient, String subject, String textBody, String htmlBody) {
    return new SendNotificationRequest(
        NotificationChannel.EMAIL,
        recipient,
        subject,
        textBody,
        false,
        textBody,
        htmlBody);
  }

  public boolean multipartAlternative() {
    return textBody != null
        && !textBody.isBlank()
        && htmlBody != null
        && !htmlBody.isBlank();
  }
}
