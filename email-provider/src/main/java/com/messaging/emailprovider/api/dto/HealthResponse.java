package com.messaging.emailprovider.api.dto;

import java.time.Instant;

public record HealthResponse(String service, String state, Instant timestamp) {

  public static HealthResponse up() {
    return new HealthResponse("email-provider", "UP", Instant.now());
  }
}
