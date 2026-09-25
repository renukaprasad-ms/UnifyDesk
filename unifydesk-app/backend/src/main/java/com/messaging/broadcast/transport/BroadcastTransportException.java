package com.messaging.broadcast.transport;

public class BroadcastTransportException extends RuntimeException {

  private final BroadcastTransportOutcome outcome;
  private final String code;

  private BroadcastTransportException(
      BroadcastTransportOutcome outcome, String code, String message, Throwable cause) {
    super(message, cause);
    this.outcome = outcome;
    this.code = code;
  }

  public static BroadcastTransportException retryable(
      String code, String message, Throwable cause) {
    return new BroadcastTransportException(
        BroadcastTransportOutcome.RETRYABLE, code, message, cause);
  }

  public static BroadcastTransportException permanent(
      String code, String message, Throwable cause) {
    return new BroadcastTransportException(
        BroadcastTransportOutcome.PERMANENT, code, message, cause);
  }

  public static BroadcastTransportException unknown(
      String code, String message, Throwable cause) {
    return new BroadcastTransportException(
        BroadcastTransportOutcome.UNKNOWN, code, message, cause);
  }

  public BroadcastTransportOutcome outcome() {
    return outcome;
  }

  public String code() {
    return code;
  }
}
