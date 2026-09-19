package com.messaging.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageStorageMeterTests {

  @Test
  void measuresStoredFieldsUsingUtf8Bytes() {
    MessageStorageMeter meter = new MessageStorageMeter();

    String externalId = "provider-1";
    String internetId = "<message@example.com>";
    String historyId = "44";
    String subject = "Hello";
    String snippet = "Preview";
    String bodyText = "Hi 👋";
    String bodyHtml = "<p>Hi 👋</p>";

    long variableBytes =
        bytes(externalId)
            + bytes(internetId)
            + bytes(historyId)
            + bytes(subject)
            + bytes(snippet)
            + bytes(bodyText)
            + bytes(bodyHtml);

    assertThat(
            meter.measure(externalId, internetId, historyId, subject, snippet, bodyText, bodyHtml))
        .isEqualTo(256 + variableBytes);
  }

  @Test
  void nullFieldsDoNotAddVariableBytes() {
    MessageStorageMeter meter = new MessageStorageMeter();

    assertThat(meter.measure(null, null, null, null, null, null, null)).isEqualTo(256);
  }

  private long bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }
}
