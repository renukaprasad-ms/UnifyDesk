package com.messaging.notification.platform.delivery.policy;

import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.EffectivePreference;
import com.messaging.notification.platform.delivery.model.DeliveryModels.QuietHours;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryEligibilityService {

  private final NotificationDeliveryRuntimeRepository repository;
  private final NotificationDeliveryPolicy policy;
  private final CompanyIamPermissionService permissionService;
  private final NotificationDeliveryProperties properties;
  private final BrowserPushReadiness pushReadiness;

  public Decision evaluate(DeliveryIntent intent, Instant now) {
    RecipientSnapshot recipient =
        repository.recipientSnapshot(intent.userId(), intent.companyId()).orElse(null);
    if (recipient == null || !recipient.active()) {
      return Decision.suppress("USER_INACTIVE");
    }
    if (intent.companyId() != null) {
      if (!recipient.companyEligible()) {
        return Decision.suppress("COMPANY_INELIGIBLE");
      }
      if (!recipient.activeMember()) {
        return Decision.suppress("MEMBERSHIP_REVOKED");
      }
      if (intent.requiredPermissionKey() != null
          && !intent.requiredPermissionKey().isBlank()
          && !permissionService.hasPermission(
              intent.userId(), intent.companyId(), intent.requiredPermissionKey())) {
        return Decision.suppress("PERMISSION_REVOKED");
      }
    }

    if (!registryPermits(intent)) {
      return Decision.suppress("CHANNEL_MODE_NOT_PERMITTED");
    }

    if (intent.channel() == Channel.EMAIL && !properties.getEmail().isEnabled()) {
      return Decision.defer(
          now.plus(properties.getEmail().getDisabledRetryDelay()), "EMAIL_CHANNEL_DISABLED");
    }

    EffectivePreference preference =
        repository.effectivePreference(
            intent.userId(), intent.companyId(), intent.notificationType());
    boolean mandatoryWelcome =
        "COMPANY_CREATED".equals(intent.notificationType())
            && intent.channel() == Channel.EMAIL
            && intent.deliveryMode() == DeliveryMode.IMMEDIATE;

    if (!mandatoryWelcome) {
      if (intent.channel() == Channel.EMAIL) {
        if (preference.emailMode() == null
            || !intent.deliveryMode().name().equals(preference.emailMode())) {
          return Decision.suppress("EMAIL_PREFERENCE_DISABLED");
        }
      } else if (!preference.browserEnabled()) {
        return Decision.suppress("BROWSER_PREFERENCE_DISABLED");
      }
    }

    if (intent.channel() == Channel.WEB_PUSH && !pushReadiness.ready()) {
      return Decision.suppress("PUSH_NOT_CONFIGURED");
    }

    if (!mandatoryWelcome) {
      QuietHours quietHours =
          repository.quietHours(intent.userId(), intent.companyId(), recipient.companyTimezone());
      Instant deferral = policy.quietHoursDeferral(now, quietHours);
      if (deferral != null) {
        return Decision.defer(deferral, "QUIET_HOURS");
      }
    }

    if (mandatoryWelcome) {
      // The in-app recipient stays the creator; only the company welcome email uses this address.
      // A user's verification flag does not verify the company's separate business address.
      String businessEmail = repository.companyBusinessEmail(intent.companyId());
      if (!validEmail(businessEmail)) return Decision.suppress("COMPANY_EMAIL_MISSING_OR_INVALID");
      recipient =
          new RecipientSnapshot(
              recipient.userId(),
              businessEmail,
              recipient.userName(),
              recipient.active(),
              recipient.verified(),
              recipient.companyId(),
              recipient.companyName(),
              recipient.companyTimezone(),
              recipient.companyEligible(),
              recipient.activeMember());
    } else if (intent.channel() == Channel.EMAIL
        && (!recipient.verified() || !validEmail(recipient.email()))) {
      return Decision.suppress("EMAIL_NOT_VERIFIED_OR_INVALID");
    }

    return Decision.send(recipient);
  }

  public boolean eligibleDigestContribution(DeliveryIntent intent) {
    if (!"INBOX_ACTIVITY".equals(intent.notificationType())
        || intent.channel() != Channel.EMAIL
        || intent.deliveryMode() != DeliveryMode.DIGEST
        || intent.companyId() == null) {
      return false;
    }
    RecipientSnapshot recipient =
        repository.recipientSnapshot(intent.userId(), intent.companyId()).orElse(null);
    if (recipient == null
        || !recipient.active()
        || !recipient.companyEligible()
        || !recipient.activeMember()) {
      return false;
    }
    if (intent.requiredPermissionKey() != null
        && !intent.requiredPermissionKey().isBlank()
        && !permissionService.hasPermission(
            intent.userId(), intent.companyId(), intent.requiredPermissionKey())) {
      return false;
    }
    EffectivePreference preference =
        repository.effectivePreference(
            intent.userId(), intent.companyId(), intent.notificationType());
    return "DIGEST".equals(preference.emailMode());
  }

  private boolean registryPermits(DeliveryIntent intent) {
    if ("COMPANY_CREATED".equals(intent.notificationType())) {
      return intent.channel() == Channel.EMAIL && intent.deliveryMode() == DeliveryMode.IMMEDIATE;
    }
    if ("INBOX_ACTIVITY".equals(intent.notificationType())) {
      return (intent.channel() == Channel.EMAIL && intent.deliveryMode() == DeliveryMode.DIGEST)
          || (intent.channel() == Channel.WEB_PUSH && intent.deliveryMode() != DeliveryMode.DIGEST);
    }
    return false;
  }

  private boolean validEmail(String email) {
    if (email == null || email.isBlank() || email.length() > 320) {
      return false;
    }
    int at = email.indexOf('@');
    return at > 0
        && at == email.lastIndexOf('@')
        && at < email.length() - 1
        && email.indexOf('\r') < 0
        && email.indexOf('\n') < 0;
  }

  public record Decision(
      Action action, String code, Instant deferUntil, RecipientSnapshot recipient) {
    public enum Action {
      SEND,
      DEFER,
      SUPPRESS
    }

    static Decision send(RecipientSnapshot recipient) {
      return new Decision(Action.SEND, null, null, recipient);
    }

    static Decision defer(Instant until, String code) {
      return new Decision(Action.DEFER, code, until, null);
    }

    static Decision suppress(String code) {
      return new Decision(Action.SUPPRESS, code, null, null);
    }
  }
}
