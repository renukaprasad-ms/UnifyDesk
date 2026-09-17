package com.messaging.channel.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.exception.PlanLimitExceededException;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelEntitlementService {

  public static final String EMAIL_ACCOUNTS = "EMAIL_ACCOUNTS";
  public static final String WHATSAPP_ACCOUNTS = "WHATSAPP_ACCOUNTS";
  public static final String WHATSAPP_NUMBERS = "WHATSAPP_NUMBERS";
  public static final String SMS_ACCOUNTS = "SMS_ACCOUNTS";
  public static final String SMS_SENDER_IDS = "SMS_SENDER_IDS";

  private final CompanySubscriptionRepository subscriptionRepository;
  private final PlanEntitlementRepository entitlementRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;

  @Transactional(readOnly = true)
  public void assertCanCreateConnection(Long companyId, ChannelType channelType) {
    String key =
        switch (channelType) {
          case EMAIL -> EMAIL_ACCOUNTS;
          case WHATSAPP -> WHATSAPP_ACCOUNTS;
          case SMS -> SMS_ACCOUNTS;
        };
    long current =
        connectionRepository.countByCompanyIdAndChannelTypeAndStatusNot(
            companyId, channelType, ChannelConnectionStatus.DISCONNECTED);
    enforce(companyId, key, current);
  }

  @Transactional(readOnly = true)
  public void assertCanCreateSender(Long companyId, SenderIdentityType identityType) {
    String key =
        switch (identityType) {
          case PHONE_NUMBER -> WHATSAPP_NUMBERS;
          case SMS_HEADER -> SMS_SENDER_IDS;
          case EMAIL_ADDRESS -> null;
        };
    if (key == null) {
      return;
    }
    long current =
        senderIdentityRepository.countByConnectionCompanyIdAndIdentityType(companyId, identityType);
    enforce(companyId, key, current);
  }

  private void enforce(Long companyId, String key, long currentUsage) {
    Optional<CompanySubscription> subscription = activeSubscription(companyId);
    if (subscription.isEmpty()) {
      return;
    }

    Optional<PlanEntitlement> entitlement =
        entitlementRepository.findByPlanIdAndKey(subscription.get().getPlan().getId(), key);
    if (entitlement.isEmpty()) {
      return;
    }

    String rawValue = entitlement.get().getValue();
    if (rawValue == null || rawValue.isBlank()) {
      return;
    }
    String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("unlimited") || normalized.equals("-1")) {
      return;
    }

    final long limit;
    try {
      limit = Long.parseLong(normalized);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid numeric entitlement value for " + key, e);
    }

    if (currentUsage >= limit) {
      throw new PlanLimitExceededException(
          "Plan limit reached for " + key + " (limit=" + limit + ")");
    }
  }

  private Optional<CompanySubscription> activeSubscription(Long companyId) {
    Optional<CompanySubscription> active =
        subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE);
    if (active.isPresent()) {
      return active;
    }
    return subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
        companyId, SubscriptionStatus.TRIALING);
  }
}
