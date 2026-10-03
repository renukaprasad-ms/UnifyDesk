package com.messaging.broadcast.transport;

import java.util.Map;

public record BroadcastEmailCommand(
    String recipient,
    String subject,
    String bodyText,
    String bodyHtml,
    String providerTemplateId,
    Map<String, String> templateVariables,
    String requestedInternetMessageId,
    String correlationId,
    Long companyId,
    Long runId,
    Long senderId,
    Long recipientId,
    BroadcastEmailComplianceMetadata compliance) {

  public BroadcastEmailCommand(
      String recipient,
      String subject,
      String bodyText,
      String bodyHtml,
      String providerTemplateId,
      Map<String, String> templateVariables,
      String requestedInternetMessageId,
      String correlationId) {
    this(
        recipient,
        subject,
        bodyText,
        bodyHtml,
        providerTemplateId,
        templateVariables,
        requestedInternetMessageId,
        correlationId,
        null,
        null,
        null,
        null,
        null);
  }

  public BroadcastEmailCommand(
      String recipient,
      String subject,
      String bodyText,
      String bodyHtml,
      String providerTemplateId,
      Map<String, String> templateVariables,
      String requestedInternetMessageId,
      String correlationId,
      Long companyId,
      Long runId,
      Long senderId,
      Long recipientId) {
    this(
        recipient,
        subject,
        bodyText,
        bodyHtml,
        providerTemplateId,
        templateVariables,
        requestedInternetMessageId,
        correlationId,
        companyId,
        runId,
        senderId,
        recipientId,
        null);
  }
}
