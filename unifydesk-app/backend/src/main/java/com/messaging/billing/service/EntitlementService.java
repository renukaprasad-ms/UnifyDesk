package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementService {

  private final CompanySubscriptionRepository subscriptionRepository;
  private final PlanEntitlementRepository entitlementRepository;
  private final EntitlementDefinitionRepository definitionRepository;

  @Transactional(readOnly = true)
  public void assertWithinLimit(Long companyId, String key, long currentUsage) {
    CompanySubscription subscription =
        effectiveSubscription(companyId)
            .orElseThrow(() -> new PlanEntitlementException("Company has no active plan"));
    if (subscription.getStatus() == SubscriptionStatus.PAST_DUE
        || subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
      throw new PlanEntitlementException(
          "Net-new entitlement expansion is blocked while subscription is "
              + subscription.getStatus());
    }
    long limit = integerValue(companyId, key);
    if (limit >= 0 && currentUsage >= limit) {
      throw new PlanEntitlementException(
          "Plan limit reached for " + normalizeKey(key) + " (limit=" + limit + ")");
    }
  }

  @Transactional(readOnly = true)
  public long integerValue(Long companyId, String key) {
    String raw = resolvedValue(companyId, key);
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("unlimited") || normalized.equals("-1")) return -1;
    try {
      return Long.parseLong(normalized);
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("Invalid integer entitlement value for " + key, exception);
    }
  }

  @Transactional(readOnly = true)
  public boolean booleanValue(Long companyId, String key) {
    String raw = resolvedValue(companyId, key).trim().toLowerCase(Locale.ROOT);
    if (!raw.equals("true") && !raw.equals("false")) {
      throw new IllegalStateException("Invalid boolean entitlement value for " + key);
    }
    return Boolean.parseBoolean(raw);
  }

  @Transactional(readOnly = true)
  public BigDecimal decimalValue(Long companyId, String key) {
    try {
      return new BigDecimal(resolvedValue(companyId, key).trim());
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("Invalid decimal entitlement value for " + key, exception);
    }
  }

  @Transactional(readOnly = true)
  public String resolvedValue(Long companyId, String key) {
    String normalizedKey = normalizeKey(key);
    EntitlementDefinition definition =
        definitionRepository
            .findByKey(normalizedKey)
            .filter(EntitlementDefinition::isActive)
            .orElseThrow(() -> new IllegalStateException("Unknown entitlement " + normalizedKey));

    CompanySubscription subscription =
        effectiveSubscription(companyId)
            .orElseThrow(() -> new PlanEntitlementException("Company has no active plan"));

    return entitlementRepository
        .findByPlanIdAndKey(subscription.getPlan().getId(), normalizedKey)
        .map(PlanEntitlement::getValue)
        .filter(value -> value != null && !value.isBlank())
        .orElse(definition.getDefaultValue());
  }

  @Transactional(readOnly = true)
  public Optional<CompanySubscription> effectiveSubscription(Long companyId) {
    Optional<CompanySubscription> active =
        subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE);
    if (active.isPresent()) return active;
    Optional<CompanySubscription> trial =
        subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.TRIALING);
    if (trial.isPresent()) return trial;
    Optional<CompanySubscription> pastDue =
        subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.PAST_DUE);
    if (pastDue.isPresent()
        && pastDue.get().getGraceEndsAt() != null
        && pastDue.get().getGraceEndsAt().isAfter(Instant.now())) {
      return pastDue;
    }
    return Optional.empty();
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Entitlement key is required");
    }
    return key.trim().toUpperCase(Locale.ROOT);
  }
}
