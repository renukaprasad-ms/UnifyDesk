package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.channel.exception.PlanLimitExceededException;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementService {

  private static final EnumSet<SubscriptionStatus> CURRENT_STATUSES =
      EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE);

  private final CompanySubscriptionRepository subscriptionRepository;
  private final PlanEntitlementRepository entitlementRepository;
  private final EntitlementDefinitionRepository definitionRepository;

  @Transactional(readOnly = true)
  public Optional<CompanySubscription> currentSubscription(Long companyId) {
    return subscriptionRepository.findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(
        companyId, CURRENT_STATUSES);
  }

  @Transactional(readOnly = true)
  public long limit(Long companyId, String key) {
    String value = value(companyId, key).orElse(null);
    if (value == null || value.isBlank()) return 0;
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("unlimited") || normalized.equals("-1")) return -1;
    try {
      return Long.parseLong(normalized);
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("Entitlement " + key + " is not an integer", exception);
    }
  }

  @Transactional(readOnly = true)
  public boolean enabled(Long companyId, String key) {
    return value(companyId, key).map(Boolean::parseBoolean).orElse(false);
  }

  @Transactional(readOnly = true)
  public Optional<String> value(Long companyId, String key) {
    CompanySubscription subscription = currentSubscription(companyId).orElse(null);
    if (subscription == null) return Optional.empty();
    return entitlementRepository
        .findByPlanIdAndKey(subscription.getPlan().getId(), normalizeKey(key))
        .map(PlanEntitlement::getValue);
  }

  @Transactional(readOnly = true)
  public void assertWithinLimit(Long companyId, String key, long currentUsage) {
    long limit = limit(companyId, key);
    if (limit == -1) return;
    if (currentUsage >= limit) {
      throw new PlanLimitExceededException(
          "Plan limit reached for " + normalizeKey(key) + " (limit=" + limit + ")");
    }
  }

  @Transactional(readOnly = true)
  public EntitlementDefinition requireDefinition(String key) {
    return definitionRepository
        .findById(normalizeKey(key))
        .filter(EntitlementDefinition::isActive)
        .orElseThrow(() -> new NotFoundException("Unknown entitlement: " + key));
  }

  public String validateValue(EntitlementDefinition definition, String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      throw new BadRequestException("Entitlement value is required");
    }
    String value = rawValue.trim();
    if (definition.isAllowUnlimited()
        && (value.equalsIgnoreCase("unlimited") || value.equals("-1"))) {
      return "-1";
    }

    try {
      if (definition.getValueType() == EntitlementValueType.INTEGER) {
        long parsed = Long.parseLong(value);
        if (parsed < 0) throw new NumberFormatException();
        return Long.toString(parsed);
      }
      if (definition.getValueType() == EntitlementValueType.DECIMAL) {
        BigDecimal parsed = new BigDecimal(value);
        if (parsed.signum() < 0) throw new NumberFormatException();
        return parsed.stripTrailingZeros().toPlainString();
      }
      if (definition.getValueType() == EntitlementValueType.BOOLEAN) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
          throw new IllegalArgumentException();
        }
        return Boolean.toString(Boolean.parseBoolean(value));
      }
      return value;
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Invalid value for " + definition.getKey() + " (expected " + definition.getValueType() + ")");
    }
  }

  private String normalizeKey(String key) {
    return key.trim().toUpperCase(Locale.ROOT);
  }
}
