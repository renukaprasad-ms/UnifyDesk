package com.messaging.channel.meta.runtime;

import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppProviderFailureClassifier {

  public Failure classify(Integer httpStatus, boolean ioFailure, boolean messageSubmission) {
    if (ioFailure) {
      return new Failure(
          "WHATSAPP_PROVIDER_TEMPORARY", true, messageSubmission);
    }
    if (httpStatus == null) {
      return new Failure(
          "WHATSAPP_PROVIDER_TEMPORARY", true, messageSubmission);
    }
    if (httpStatus == 401 || httpStatus == 403) {
      return new Failure("WHATSAPP_REAUTH_REQUIRED", false, false);
    }
    if (httpStatus == 429 || httpStatus >= 500) {
      return new Failure("WHATSAPP_PROVIDER_TEMPORARY", true, false);
    }
    return new Failure("WHATSAPP_PROVIDER_REJECTED", false, false);
  }

  public record Failure(String safeCategory, boolean retryable, boolean ambiguousOutcome) {}
}
