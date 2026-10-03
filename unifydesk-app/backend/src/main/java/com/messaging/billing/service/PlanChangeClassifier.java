package com.messaging.billing.service;

import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.billing.enums.PlanChangeDirection;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanChangeClassifier {

  private final EntitlementDefinitionRepository definitionRepository;
  private final PlanEntitlementRepository entitlementRepository;

  @Transactional(readOnly = true)
  public PlanChangeDirection classify(
      Plan currentPlan, PlanPrice currentPrice, Plan targetPlan, PlanPrice targetPrice) {
    boolean increases = false;
    boolean decreases = false;

    for (EntitlementDefinition definition :
        definitionRepository.findByActiveTrueOrderByCategoryAscNameAsc()) {
      String currentValue = value(currentPlan.getId(), definition);
      String targetValue = value(targetPlan.getId(), definition);
      Integer comparison = compare(definition, currentValue, targetValue);
      if (comparison == null) {
        return PlanChangeDirection.INCOMPARABLE;
      }
      if (comparison < 0) {
        decreases = true;
      } else if (comparison > 0) {
        increases = true;
      }
      if (increases && decreases) {
        return PlanChangeDirection.INCOMPARABLE;
      }
    }

    if (increases) {
      return PlanChangeDirection.UPGRADE;
    }
    if (decreases) {
      return PlanChangeDirection.DOWNGRADE;
    }

    int priceComparison =
        normalizedAnnual(targetPrice).compareTo(normalizedAnnual(currentPrice));
    if (priceComparison > 0) {
      return PlanChangeDirection.UPGRADE;
    }
    if (priceComparison < 0) {
      return PlanChangeDirection.DOWNGRADE;
    }
    return PlanChangeDirection.EQUIVALENT;
  }

  private String value(Long planId, EntitlementDefinition definition) {
    return entitlementRepository
        .findByPlanIdAndKey(planId, definition.getKey())
        .map(PlanEntitlement::getValue)
        .filter(value -> value != null && !value.isBlank())
        .orElse(definition.getDefaultValue());
  }

  private Integer compare(
      EntitlementDefinition definition, String currentValue, String targetValue) {
    return switch (definition.getValueType()) {
      case BOOLEAN ->
          Boolean.compare(parseBoolean(targetValue, definition), parseBoolean(currentValue, definition));
      case INTEGER ->
          compareNumericLike(currentValue, targetValue, definition, true);
      case DECIMAL ->
          compareNumericLike(currentValue, targetValue, definition, false);
      case TEXT ->
          Objects.equals(normalize(currentValue), normalize(targetValue)) ? 0 : null;
    };
  }

  private int compareNumericLike(
      String currentValue,
      String targetValue,
      EntitlementDefinition definition,
      boolean integer) {
    boolean currentUnlimited = unlimited(currentValue);
    boolean targetUnlimited = unlimited(targetValue);
    if (currentUnlimited || targetUnlimited) {
      return Boolean.compare(targetUnlimited, currentUnlimited);
    }
    try {
      BigDecimal current =
          integer
              ? BigDecimal.valueOf(Long.parseLong(currentValue.trim()))
              : new BigDecimal(currentValue.trim());
      BigDecimal target =
          integer
              ? BigDecimal.valueOf(Long.parseLong(targetValue.trim()))
              : new BigDecimal(targetValue.trim());
      return target.compareTo(current);
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Invalid entitlement value for authoritative comparison: " + definition.getKey());
    }
  }

  private boolean parseBoolean(String value, EntitlementDefinition definition) {
    String normalized = normalize(value);
    if (!"true".equals(normalized) && !"false".equals(normalized)) {
      throw new BadRequestException(
          "Invalid entitlement value for authoritative comparison: " + definition.getKey());
    }
    return Boolean.parseBoolean(normalized);
  }

  private boolean unlimited(String value) {
    String normalized = normalize(value);
    return "unlimited".equals(normalized) || "-1".equals(normalized);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private BigDecimal normalizedAnnual(PlanPrice price) {
    if (price == null) {
      throw new BadRequestException("Plan change requires immutable current and target prices");
    }
    BillingCycle cycle = price.getBillingCycle();
    return switch (cycle) {
      case FREE -> BigDecimal.ZERO;
      case MONTHLY -> price.getAmount().multiply(BigDecimal.valueOf(12));
      case YEARLY -> price.getAmount();
    };
  }
}
