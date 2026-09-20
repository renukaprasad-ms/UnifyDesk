package com.messaging.kafka.event;

public record OtpRequestedEvent(
    String identifier,
    String deliveryChannel,
    String subject,
    String body,
    boolean html,
    String textBody,
    String htmlBody) {

  public OtpRequestedEvent(
      String identifier,
      String deliveryChannel,
      String subject,
      String body,
      boolean html) {
    this(identifier, deliveryChannel, subject, body, html, null, null);
  }

  public static OtpRequestedEvent multipartEmail(
      String identifier, String subject, String textBody, String htmlBody) {
    return new OtpRequestedEvent(
        identifier,
        "EMAIL",
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
