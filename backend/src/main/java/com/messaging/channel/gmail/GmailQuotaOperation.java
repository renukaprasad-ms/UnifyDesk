package com.messaging.channel.gmail;

/**
 * Gmail API quota costs documented by Google. Keep these values centralized so every provider
 * operation is accounted for consistently.
 */
public enum GmailQuotaOperation {
  GET_PROFILE(1),
  LIST_MESSAGES(5),
  GET_MESSAGE(20),
  GET_ATTACHMENT(20),
  HISTORY_LIST(2),
  MODIFY_MESSAGE(5),
  SEND_MESSAGE(100),
  WATCH(100),
  STOP_WATCH(50);

  private final int units;

  GmailQuotaOperation(int units) {
    this.units = units;
  }

  public int units() {
    return units;
  }

  public long cost(int count) {
    return Math.multiplyExact((long) units, Math.max(1, count));
  }
}
