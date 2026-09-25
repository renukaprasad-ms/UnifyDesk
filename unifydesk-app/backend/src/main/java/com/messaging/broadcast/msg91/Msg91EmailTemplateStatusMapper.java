package com.messaging.broadcast.msg91;

import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;
import java.util.Locale;

final class Msg91EmailTemplateStatusMapper {

  private Msg91EmailTemplateStatusMapper() {}

  static EmailTemplateProviderBindingStatus map(String providerStatus) {
    if (providerStatus == null || providerStatus.isBlank()) {
      return EmailTemplateProviderBindingStatus.SYNCED;
    }

    String value = providerStatus.trim().toLowerCase(Locale.ROOT);

    if (containsAny(
        value,
        "reject",
        "denied",
        "blocked",
        "disabled",
        "failed",
        "inactive",
        "not approved",
        "unapproved",
        "disapproved")) {
      return EmailTemplateProviderBindingStatus.REJECTED;
    }

    if (containsAny(value, "pending", "review", "queued", "draft", "waiting", "submitted")) {
      return EmailTemplateProviderBindingStatus.PENDING_REVIEW;
    }

    if (containsAny(value, "approved", "active", "live", "published", "verified")) {
      return EmailTemplateProviderBindingStatus.READY;
    }

    return EmailTemplateProviderBindingStatus.SYNCED;
  }

  private static boolean containsAny(String value, String... parts) {
    for (String part : parts) {
      if (value.contains(part)) return true;
    }
    return false;
  }
}
