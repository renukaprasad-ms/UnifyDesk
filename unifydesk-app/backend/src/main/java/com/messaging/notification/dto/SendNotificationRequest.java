package com.messaging.notification.dto;

import com.messaging.notification.model.NotificationChannel;

public record SendNotificationRequest(
    NotificationChannel channel,
    String recipient,
    String subject,
    String body,
    boolean html,
    String textBody,
    String htmlBody,
    String senderPurpose) {

  public SendNotificationRequest(
      NotificationChannel channel,
      String recipient,
      String subject,
      String body,
      boolean html) {
    this(channel, recipient, subject, body, html, null, null, null);
  }

  public SendNotificationRequest(NotificationChannel channel, String recipient, String subject, String body, boolean html, String textBody, String htmlBody) {
    this(channel, recipient, subject, body, html, textBody, htmlBody, null);
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

  public static SendNotificationRequest billingEmail(String recipient, String subject, String body, boolean html) {
    return email(recipient, subject, body, html).withSenderPurpose("BILLING");
  }

  public static SendNotificationRequest securityEmail(String recipient, String subject, String body, boolean html) {
    return email(recipient, subject, body, html).withSenderPurpose("SECURITY");
  }

  public SendNotificationRequest withSenderPurpose(String purpose) {
    return new SendNotificationRequest(channel, recipient, subject, body, html, textBody, htmlBody, purpose);
  }

  public boolean multipartAlternative() {
    return textBody != null
        && !textBody.isBlank()
        && htmlBody != null
        && !htmlBody.isBlank();
  }
}
