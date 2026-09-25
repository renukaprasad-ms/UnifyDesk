package com.messaging.emailtemplate.enums;

public enum EmailTemplateProviderBindingStatus {
  SYNCING,
  PENDING_REVIEW,
  SYNCED,
  READY,
  REJECTED,
  FAILED;

  public boolean sendable() {
    return this == READY;
  }
}
