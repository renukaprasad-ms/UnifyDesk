package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class MetaWhatsAppRuntimeGraphClientRetryAfterTests {

  @Test
  void parsesRetryAfterSeconds() {
    assertThat(
            MetaWhatsAppRuntimeGraphClient.parseRetryAfter(
                "7", Instant.parse("2026-09-29T00:00:00Z")))
        .isEqualTo(Duration.ofSeconds(7));
  }

  @Test
  void parsesRetryAfterHttpDate() {
    Instant now = Instant.parse("2026-09-29T00:00:00Z");
    String retryAt =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(11).atZone(ZoneOffset.UTC));

    assertThat(MetaWhatsAppRuntimeGraphClient.parseRetryAfter(retryAt, now))
        .isEqualTo(Duration.ofSeconds(11));
  }

  @Test
  void invalidRetryAfterFallsBackToCallerPolicy() {
    assertThat(
            MetaWhatsAppRuntimeGraphClient.parseRetryAfter(
                "not-a-delay", Instant.parse("2026-09-29T00:00:00Z")))
        .isNull();
  }
}
