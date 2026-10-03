package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.billing.enums.PlanChangeDirection;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanChangeClassifierTests {

  @Mock private EntitlementDefinitionRepository definitionRepository;
  @Mock private PlanEntitlementRepository entitlementRepository;

  @InjectMocks private PlanChangeClassifier classifier;

  @Test
  void classifiesStrictEntitlementIncreaseAsUpgrade() {
    Plan current = plan(1L);
    Plan target = plan(2L);
    EntitlementDefinition contacts = definition("CONTACT_LIMIT", EntitlementValueType.INTEGER, "0");
    when(definitionRepository.findByActiveTrueOrderByCategoryAscNameAsc())
        .thenReturn(List.of(contacts));
    when(entitlementRepository.findByPlanIdAndKey(1L, "CONTACT_LIMIT"))
        .thenReturn(Optional.of(entitlement(current, "CONTACT_LIMIT", "100")));
    when(entitlementRepository.findByPlanIdAndKey(2L, "CONTACT_LIMIT"))
        .thenReturn(Optional.of(entitlement(target, "CONTACT_LIMIT", "1000")));

    assertEquals(
        PlanChangeDirection.UPGRADE,
        classifier.classify(
            current,
            price(11L, current, "100", BillingCycle.MONTHLY),
            target,
            price(12L, target, "200", BillingCycle.MONTHLY)));
  }

  @Test
  void classifiesStrictEntitlementDecreaseAsDowngrade() {
    Plan current = plan(1L);
    Plan target = plan(2L);
    EntitlementDefinition contacts = definition("CONTACT_LIMIT", EntitlementValueType.INTEGER, "0");
    when(definitionRepository.findByActiveTrueOrderByCategoryAscNameAsc())
        .thenReturn(List.of(contacts));
    when(entitlementRepository.findByPlanIdAndKey(1L, "CONTACT_LIMIT"))
        .thenReturn(Optional.of(entitlement(current, "CONTACT_LIMIT", "1000")));
    when(entitlementRepository.findByPlanIdAndKey(2L, "CONTACT_LIMIT"))
        .thenReturn(Optional.of(entitlement(target, "CONTACT_LIMIT", "100")));

    assertEquals(
        PlanChangeDirection.DOWNGRADE,
        classifier.classify(
            current,
            price(11L, current, "200", BillingCycle.MONTHLY),
            target,
            price(12L, target, "100", BillingCycle.MONTHLY)));
  }

  @Test
  void mixedEntitlementMovementFailsClosedAsIncomparable() {
    Plan current = plan(1L);
    Plan target = plan(2L);
    EntitlementDefinition contacts = definition("CONTACT_LIMIT", EntitlementValueType.INTEGER, "0");
    EntitlementDefinition api = definition("API_ACCESS_ENABLED", EntitlementValueType.BOOLEAN, "false");
    when(definitionRepository.findByActiveTrueOrderByCategoryAscNameAsc())
        .thenReturn(List.of(contacts, api));
    when(entitlementRepository.findByPlanIdAndKey(1L, "CONTACT_LIMIT"))
        .thenReturn(Optional.of(entitlement(current, "CONTACT_LIMIT", "1000")));
    when(entitlementRepository.findByPlanIdAndKey(2L, "CONTACT_LIMIT"))
        .thenReturn(Optional.of(entitlement(target, "CONTACT_LIMIT", "100")));
    when(entitlementRepository.findByPlanIdAndKey(1L, "API_ACCESS_ENABLED"))
        .thenReturn(Optional.of(entitlement(current, "API_ACCESS_ENABLED", "false")));
    when(entitlementRepository.findByPlanIdAndKey(2L, "API_ACCESS_ENABLED"))
        .thenReturn(Optional.of(entitlement(target, "API_ACCESS_ENABLED", "true")));

    assertEquals(
        PlanChangeDirection.INCOMPARABLE,
        classifier.classify(
            current,
            price(11L, current, "100", BillingCycle.MONTHLY),
            target,
            price(12L, target, "200", BillingCycle.MONTHLY)));
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    return plan;
  }

  private PlanPrice price(Long id, Plan plan, String amount, BillingCycle cycle) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setAmount(new BigDecimal(amount));
    price.setBillingCycle(cycle);
    return price;
  }

  private EntitlementDefinition definition(
      String key, EntitlementValueType type, String defaultValue) {
    EntitlementDefinition definition = new EntitlementDefinition();
    definition.setKey(key);
    definition.setValueType(type);
    definition.setCategory(EntitlementCategory.WORKSPACE);
    definition.setDefaultValue(defaultValue);
    definition.setActive(true);
    return definition;
  }

  private PlanEntitlement entitlement(Plan plan, String key, String value) {
    PlanEntitlement entitlement = new PlanEntitlement();
    entitlement.setPlan(plan);
    entitlement.setKey(key);
    entitlement.setValue(value);
    return entitlement;
  }
}
