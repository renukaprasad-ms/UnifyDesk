package com.messaging.contacts.consent;

public class InvalidRecipientActionException extends RuntimeException {

  public InvalidRecipientActionException() {
    super("RECIPIENT_ACTION_INVALID");
  }

  public InvalidRecipientActionException(Throwable cause) {
    super("RECIPIENT_ACTION_INVALID", cause);
  }
}
