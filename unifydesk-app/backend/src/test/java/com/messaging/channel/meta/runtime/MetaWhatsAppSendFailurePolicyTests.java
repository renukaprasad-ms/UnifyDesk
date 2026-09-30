package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetaWhatsAppSendFailurePolicyTests {

  private final MetaWhatsAppSendFailurePolicy policy =
      new MetaWhatsAppSendFailurePolicy();

  @Test
  void ambiguousTimeoutMapsToUnknownAndNeverBlindRetryOutcome() {
    MetaWhatsAppProviderException timeout =
        new MetaWhatsAppProviderException(
            null,
            null,
            null,
            null,
            "PROVIDER_TEMPORARY",
            true,
            true,
            new RuntimeException("timeout after dispatch"));

    assertThat(policy.outcome(timeout))
        .isEqualTo(MetaWhatsAppSendFailurePolicy.Outcome.UNKNOWN);
  }

  @Test
  void definiteRateLimitAndRejectionRemainDistinct() {
    MetaWhatsAppProviderException rateLimited =
        new MetaWhatsAppProviderException(
            429, null, null, null, "PROVIDER_TEMPORARY", true, false, null);
    MetaWhatsAppProviderException rejected =
        new MetaWhatsAppProviderException(
            400, null, null, null, "PROVIDER_REJECTED", false, false, null);

    assertThat(policy.outcome(rateLimited))
        .isEqualTo(MetaWhatsAppSendFailurePolicy.Outcome.RETRYABLE_FAILURE);
    assertThat(policy.outcome(rejected))
        .isEqualTo(MetaWhatsAppSendFailurePolicy.Outcome.REJECTED);
  }
}
