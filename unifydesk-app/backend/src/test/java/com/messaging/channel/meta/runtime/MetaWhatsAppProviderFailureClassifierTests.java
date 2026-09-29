package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetaWhatsAppProviderFailureClassifierTests {

  private final MetaWhatsAppProviderFailureClassifier classifier =
      new MetaWhatsAppProviderFailureClassifier();

  @Test
  void provider400IsDefiniteRejection() {
    var failure = classifier.classify(400, false, true);

    assertThat(failure.safeCategory()).isEqualTo("WHATSAPP_PROVIDER_REJECTED");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.ambiguousOutcome()).isFalse();
  }

  @Test
  void provider429And5xxAreRetryableButDefiniteHttpResponses() {
    var limited = classifier.classify(429, false, true);
    var unavailable = classifier.classify(503, false, true);

    assertThat(limited.retryable()).isTrue();
    assertThat(limited.ambiguousOutcome()).isFalse();
    assertThat(unavailable.retryable()).isTrue();
    assertThat(unavailable.ambiguousOutcome()).isFalse();
  }

  @Test
  void messageSubmissionIoTimeoutIsAmbiguousUnknownCandidate() {
    var failure = classifier.classify(null, true, true);

    assertThat(failure.safeCategory()).isEqualTo("WHATSAPP_PROVIDER_TEMPORARY");
    assertThat(failure.retryable()).isTrue();
    assertThat(failure.ambiguousOutcome()).isTrue();
  }

  @Test
  void mediaUploadIoTimeoutIsNotAmbiguousMessageSubmission() {
    var failure = classifier.classify(null, true, false);

    assertThat(failure.retryable()).isTrue();
    assertThat(failure.ambiguousOutcome()).isFalse();
  }
}
