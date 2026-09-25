package com.messaging.channel.email;

public enum EmailProviderOperation {
  SEND("send"),
  SYNC("sync"),
  ACTIVE_SYNC("active_sync"),
  READ_STATE("read_state"),
  DISCONNECT("disconnect");

  private final String metricTag;

  EmailProviderOperation(String metricTag) {
    this.metricTag = metricTag;
  }

  public String metricTag() {
    return metricTag;
  }
}
