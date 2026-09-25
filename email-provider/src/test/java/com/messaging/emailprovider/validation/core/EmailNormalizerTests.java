package com.messaging.emailprovider.validation.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTests {

  private final EmailNormalizer normalizer = new EmailNormalizer();

  @Test
  void normalizesDomainAndPreservesLocalPart() {
    NormalizedEmail result = normalizer.normalize(" User.Name@EXAMPLE.COM ").orElseThrow();

    assertThat(result.value()).isEqualTo("User.Name@example.com");
    assertThat(result.localPart()).isEqualTo("User.Name");
    assertThat(result.domain()).isEqualTo("example.com");
  }

  @Test
  void rejectsMalformedAddresses() {
    assertThat(normalizer.normalize("missing-at.example.com")).isEmpty();
    assertThat(normalizer.normalize("a..b@example.com")).isEmpty();
    assertThat(normalizer.normalize("@example.com")).isEmpty();
    assertThat(normalizer.normalize("user@localhost")).isEmpty();
  }

  @Test
  void generatesStableDedupeKey() {
    assertThat(normalizer.dedupeKey("User@example.COM"))
        .isEqualTo(normalizer.dedupeKey("User@example.com"));
  }
}
