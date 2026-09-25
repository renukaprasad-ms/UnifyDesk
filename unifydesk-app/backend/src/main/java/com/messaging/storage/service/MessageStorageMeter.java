package com.messaging.storage.service;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class MessageStorageMeter {

  private static final long MESSAGE_METADATA_OVERHEAD_BYTES = 256;

  public long measure(
      String externalMessageId,
      String internetMessageId,
      String providerHistoryId,
      String subject,
      String snippet,
      String bodyText,
      String bodyHtml) {
    return MESSAGE_METADATA_OVERHEAD_BYTES
        + utf8Bytes(externalMessageId)
        + utf8Bytes(internetMessageId)
        + utf8Bytes(providerHistoryId)
        + utf8Bytes(subject)
        + utf8Bytes(snippet)
        + utf8Bytes(bodyText)
        + utf8Bytes(bodyHtml);
  }

  private long utf8Bytes(String value) {
    return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
  }
}
