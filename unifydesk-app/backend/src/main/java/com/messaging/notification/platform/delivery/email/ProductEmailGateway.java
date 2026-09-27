package com.messaging.notification.platform.delivery.email;

import com.messaging.notification.platform.delivery.model.DeliveryModels.RenderedEmail;

public interface ProductEmailGateway {

  EmailSendResult send(String recipient, RenderedEmail email);

  record EmailSendResult(Outcome outcome, String errorCode) {
    public enum Outcome {
      ACCEPTED,
      TRANSIENT_FAILURE,
      PERMANENT_FAILURE,
      UNKNOWN
    }

    public static EmailSendResult accepted() {
      return new EmailSendResult(Outcome.ACCEPTED, null);
    }

    public static EmailSendResult transientFailure(String code) {
      return new EmailSendResult(Outcome.TRANSIENT_FAILURE, code);
    }

    public static EmailSendResult permanentFailure(String code) {
      return new EmailSendResult(Outcome.PERMANENT_FAILURE, code);
    }

    public static EmailSendResult unknown(String code) {
      return new EmailSendResult(Outcome.UNKNOWN, code);
    }
  }
}
