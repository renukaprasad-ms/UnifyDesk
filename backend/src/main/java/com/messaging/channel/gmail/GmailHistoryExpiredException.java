package com.messaging.channel.gmail;

public class GmailHistoryExpiredException extends RuntimeException {

  public GmailHistoryExpiredException() {
    super("Gmail history cursor is no longer available");
  }
}
