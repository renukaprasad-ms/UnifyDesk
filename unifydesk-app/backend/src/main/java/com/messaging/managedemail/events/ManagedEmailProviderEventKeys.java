package com.messaging.managedemail.events;

final class ManagedEmailProviderEventKeys {

  private ManagedEmailProviderEventKeys() {}

  static String normalized(ManagedEmailProviderEvent event) {
    return event.providerMessageId()
        + ":"
        + event.type().name()
        + ":"
        + event.eventTimestamp().toEpochMilli()
        + ":"
        + event.recipientId();
  }
}
