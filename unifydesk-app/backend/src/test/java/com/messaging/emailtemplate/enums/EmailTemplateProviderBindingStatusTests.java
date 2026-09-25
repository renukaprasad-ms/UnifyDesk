package com.messaging.emailtemplate.enums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailTemplateProviderBindingStatusTests {

  @Test
  void onlyReadyBindingsCanSend() {
    assertFalse(EmailTemplateProviderBindingStatus.SYNCED.sendable());
    assertTrue(EmailTemplateProviderBindingStatus.READY.sendable());

    assertFalse(EmailTemplateProviderBindingStatus.SYNCING.sendable());
    assertFalse(EmailTemplateProviderBindingStatus.PENDING_REVIEW.sendable());
    assertFalse(EmailTemplateProviderBindingStatus.REJECTED.sendable());
    assertFalse(EmailTemplateProviderBindingStatus.FAILED.sendable());
  }
}
