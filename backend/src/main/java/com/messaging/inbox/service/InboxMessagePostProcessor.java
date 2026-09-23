package com.messaging.inbox.service;

import com.messaging.inbox.entity.InboxMessage;

/** Lightweight extension point invoked after a normalized inbound message has been persisted. */
public interface InboxMessagePostProcessor {
  void afterPersist(InboxMessage message);
}
