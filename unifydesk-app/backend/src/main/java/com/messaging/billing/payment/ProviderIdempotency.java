package com.messaging.billing.payment;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class ProviderIdempotency {
  private ProviderIdempotency() {}

  static String key(String operation, long id) {
    return key(operation, String.valueOf(id));
  }

  static String key(String operation, String id) {
    return UUID.nameUUIDFromBytes(
            ("unifydesk:billing:" + operation + ":" + id).getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
