package com.messaging.kafka.event;

public record OtpRequestedEvent(
    String identifier,
    String deliveryChannel,
    String subject,
    String body,
    boolean html,
    String textBody,
    String htmlBody,
    String senderPurpose) {

  public OtpRequestedEvent(
      String identifier,
      String deliveryChannel,
      String subject,
      String body,
      boolean html) {
    this(identifier, deliveryChannel, subject, body, html, null, null, null);
  }

  public OtpRequestedEvent(String identifier, String deliveryChannel, String subject, String body, boolean html, String textBody, String htmlBody) {
    this(identifier, deliveryChannel, subject, body, html, textBody, htmlBody, null);
  }

  public static OtpRequestedEvent multipartEmail(
      String identifier, String subject, String textBody, String htmlBody) {
    return multipartEmail(identifier, subject, textBody, htmlBody, null);
  }

  public static OtpRequestedEvent multipartEmail(String identifier, String subject, String textBody, String htmlBody, String senderPurpose) {
    return new OtpRequestedEvent(
        identifier,
        "EMAIL",
        subject,
        textBody,
        false,
        textBody,
        htmlBody,
        senderPurpose);
  }

  public boolean multipartAlternative() {
    return textBody != null
        && !textBody.isBlank()
        && htmlBody != null
        && !htmlBody.isBlank();
  }
}
