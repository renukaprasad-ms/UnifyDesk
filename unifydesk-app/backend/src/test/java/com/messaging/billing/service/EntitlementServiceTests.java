package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTests {

  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private PlanEntitlementRepository entitlementRepository;
  @Mock private EntitlementDefinitionRepository definitionRepository;

  @InjectMocks private EntitlementService entitlementService;

  @Test
  void missingPlanValueUsesDefinitionDefaultAndFailsClosed() {
    long companyId = 10L;
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    EntitlementDefinition definition = definition("EMAIL_ACCOUNTS", "0");

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(definitionRepository.findByKey("EMAIL_ACCOUNTS")).thenReturn(Optional.of(definition));
    when(entitlementRepository.findByPlanIdAndKey(plan.getId(), "EMAIL_ACCOUNTS"))
        .thenReturn(Optional.empty());

    assertThrows(
        PlanEntitlementException.class,
        () -> entitlementService.assertWithinLimit(companyId, "EMAIL_ACCOUNTS", 0));
  }

  @Test
  void unlimitedPlanValueDoesNotBlockUsage() {
    long companyId = 10L;
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    EntitlementDefinition definition = definition("EMAIL_ACCOUNTS", "0");
    PlanEntitlement entitlement = new PlanEntitlement();
    entitlement.setPlan(plan);
    entitlement.setKey("EMAIL_ACCOUNTS");
    entitlement.setValue("unlimited");

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(definitionRepository.findByKey("EMAIL_ACCOUNTS")).thenReturn(Optional.of(definition));
    when(entitlementRepository.findByPlanIdAndKey(plan.getId(), "EMAIL_ACCOUNTS"))
        .thenReturn(Optional.of(entitlement));

    assertDoesNotThrow(
        () -> entitlementService.assertWithinLimit(companyId, "EMAIL_ACCOUNTS", 100_000));
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    return plan;
  }

  private CompanySubscription subscription(Plan plan) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setPlan(plan);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    return subscription;
  }

  private EntitlementDefinition definition(String key, String defaultValue) {
    EntitlementDefinition definition = new EntitlementDefinition();
    definition.setKey(key);
    definition.setName(key);
    definition.setValueType(EntitlementValueType.INTEGER);
    definition.setCategory(EntitlementCategory.CHANNEL);
    definition.setDefaultValue(defaultValue);
    definition.setActive(true);
    return definition;
  }
}
