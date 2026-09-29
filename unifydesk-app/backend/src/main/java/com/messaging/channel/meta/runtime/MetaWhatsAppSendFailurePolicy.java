package com.messaging.channel.meta.runtime;

import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppSendFailurePolicy {

  public Outcome outcome(MetaWhatsAppProviderException exception) {
    if (exception.ambiguousOutcome()) return Outcome.UNKNOWN;
    return exception.retryable() ? Outcome.RETRYABLE_FAILURE : Outcome.REJECTED;
  }

  public enum Outcome {
    UNKNOWN,
    RETRYABLE_FAILURE,
    REJECTED
  }
}
