package com.messaging.realtime.dto;

import java.time.Instant;

public record RealtimeEnvelope<T>(String type, Instant occurredAt, T payload) {

  public static <T> RealtimeEnvelope<T> of(String type, T payload) {
    return new RealtimeEnvelope<>(type, Instant.now(), payload);
  }
}
