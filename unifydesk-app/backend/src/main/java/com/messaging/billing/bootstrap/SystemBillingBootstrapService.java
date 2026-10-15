package com.messaging.billing.bootstrap;

import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemBillingBootstrapService {

  public static final String FREE_PLAN_CODE = "FREE";
  public static final int SYSTEM_PLAN_VERSION = 1;

  private static final Map<String, String> FREE_ENTITLEMENTS =
      Map.ofEntries(
          Map.entry("MAX_WORKSPACE_USERS", "1"),
          Map.entry("CONTACT_LIMIT", "500"),
          Map.entry("SEGMENT_LIMIT", "5"),
          Map.entry("BROADCAST_GROUP_LIMIT", "5"),
          Map.entry("EMAIL_ACCOUNTS", "1"),
          Map.entry("WHATSAPP_ACCOUNTS", "1"),
          Map.entry("WHATSAPP_NUMBERS", "1"),
          Map.entry("SMS_ACCOUNTS", "1"),
          Map.entry("SMS_SENDER_IDS", "1"),
          Map.entry("BROADCAST_BATCH_LIMIT", "100"),
          Map.entry("BROADCASTS_PER_DAY", "5"),
          Map.entry("BROADCASTS_PER_MONTH", "50"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "250"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "2500"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "false"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "10"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "10"),
          Map.entry("SMS_TEMPLATE_LIMIT", "10"),
          Map.entry("STORAGE_GB", "1"),
          Map.entry("AUTO_REPLY_ENABLED", "false"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "false"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "1"),
          Map.entry("MANAGED_EMAIL_SENDERS", "1"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "250"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "0"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "10"),
          Map.entry("CONTACT_LIST_LIMIT", "5"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "500"));

  private static final Map<String, String> STARTER_ENTITLEMENTS =
      Map.ofEntries(
          Map.entry("MAX_WORKSPACE_USERS", "3"),
          Map.entry("CONTACT_LIMIT", "5000"),
          Map.entry("SEGMENT_LIMIT", "25"),
          Map.entry("BROADCAST_GROUP_LIMIT", "25"),
          Map.entry("EMAIL_ACCOUNTS", "2"),
          Map.entry("WHATSAPP_ACCOUNTS", "2"),
          Map.entry("WHATSAPP_NUMBERS", "2"),
          Map.entry("SMS_ACCOUNTS", "2"),
          Map.entry("SMS_SENDER_IDS", "2"),
          Map.entry("BROADCAST_BATCH_LIMIT", "1000"),
          Map.entry("BROADCASTS_PER_DAY", "25"),
          Map.entry("BROADCASTS_PER_MONTH", "500"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "5000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "50000"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "true"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "50"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "50"),
          Map.entry("SMS_TEMPLATE_LIMIT", "50"),
          Map.entry("STORAGE_GB", "5"),
          Map.entry("AUTO_REPLY_ENABLED", "true"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "false"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "3"),
          Map.entry("MANAGED_EMAIL_SENDERS", "5"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "3000"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "150"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "50"),
          Map.entry("CONTACT_LIST_LIMIT", "50"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "10000"));

  private static final Map<String, String> GROWTH_ENTITLEMENTS =
      Map.ofEntries(
          Map.entry("MAX_WORKSPACE_USERS", "8"),
          Map.entry("CONTACT_LIMIT", "25000"),
          Map.entry("SEGMENT_LIMIT", "100"),
          Map.entry("BROADCAST_GROUP_LIMIT", "100"),
          Map.entry("EMAIL_ACCOUNTS", "5"),
          Map.entry("WHATSAPP_ACCOUNTS", "5"),
          Map.entry("WHATSAPP_NUMBERS", "5"),
          Map.entry("SMS_ACCOUNTS", "5"),
          Map.entry("SMS_SENDER_IDS", "5"),
          Map.entry("BROADCAST_BATCH_LIMIT", "5000"),
          Map.entry("BROADCASTS_PER_DAY", "100"),
          Map.entry("BROADCASTS_PER_MONTH", "2000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "25000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "250000"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "true"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "200"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "200"),
          Map.entry("SMS_TEMPLATE_LIMIT", "200"),
          Map.entry("STORAGE_GB", "25"),
          Map.entry("AUTO_REPLY_ENABLED", "true"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "10"),
          Map.entry("MANAGED_EMAIL_SENDERS", "15"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "15000"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "750"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "100"),
          Map.entry("CONTACT_LIST_LIMIT", "200"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "50000"));

  private static final Map<String, String> ENTERPRISE_ENTITLEMENTS =
      Map.ofEntries(
          Map.entry("MAX_WORKSPACE_USERS", "25"),
          Map.entry("CONTACT_LIMIT", "100000"),
          Map.entry("SEGMENT_LIMIT", "500"),
          Map.entry("BROADCAST_GROUP_LIMIT", "500"),
          Map.entry("EMAIL_ACCOUNTS", "10"),
          Map.entry("WHATSAPP_ACCOUNTS", "10"),
          Map.entry("WHATSAPP_NUMBERS", "10"),
          Map.entry("SMS_ACCOUNTS", "10"),
          Map.entry("SMS_SENDER_IDS", "10"),
          Map.entry("BROADCAST_BATCH_LIMIT", "20000"),
          Map.entry("BROADCASTS_PER_DAY", "500"),
          Map.entry("BROADCASTS_PER_MONTH", "10000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "100000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "1000000"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "true"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "1000"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "1000"),
          Map.entry("SMS_TEMPLATE_LIMIT", "1000"),
          Map.entry("STORAGE_GB", "100"),
          Map.entry("AUTO_REPLY_ENABLED", "true"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "25"),
          Map.entry("MANAGED_EMAIL_SENDERS", "50"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "50000"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "3000"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "500"),
          Map.entry("CONTACT_LIST_LIMIT", "1000"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "250000"));

  private static final List<SystemPlanSeed> SYSTEM_PLANS =
      List.of(
          new SystemPlanSeed(
              "FREE",
              "Free",
              "Essential tools for solopreneurs & small teams starting out with CRM and communication",
              "FREE",
              BigDecimal.ZERO,
              List.of(new SystemPriceSeed("FREE", BigDecimal.ZERO)),
              FREE_ENTITLEMENTS),
          new SystemPlanSeed(
              "STARTER",
              "Starter",
              "Essential CRM, multi-channel messaging & automations for growing local businesses",
              "MONTHLY",
              new BigDecimal("1499"),
              List.of(
                  new SystemPriceSeed("MONTHLY", new BigDecimal("1499")),
                  new SystemPriceSeed("YEARLY", new BigDecimal("14990"))),
              STARTER_ENTITLEMENTS),
          new SystemPlanSeed(
              "GROWTH",
              "Growth",
              "High-volume communications, team collaboration, and automation for scaling businesses",
              "MONTHLY",
              new BigDecimal("3499"),
              List.of(
                  new SystemPriceSeed("MONTHLY", new BigDecimal("3499")),
                  new SystemPriceSeed("YEARLY", new BigDecimal("34990"))),
              GROWTH_ENTITLEMENTS),
          new SystemPlanSeed(
              "ENTERPRISE",
              "Enterprise",
              "Dedicated infrastructure, maximum limits, API integrations, and 24/7 priority support",
              "MONTHLY",
              new BigDecimal("8999"),
              List.of(
                  new SystemPriceSeed("MONTHLY", new BigDecimal("8999")),
                  new SystemPriceSeed("YEARLY", new BigDecimal("89990"))),
              ENTERPRISE_ENTITLEMENTS));

  private final JdbcTemplate jdbcTemplate;
  private final SystemEntitlementCatalogInitializer entitlementCatalogInitializer;

  /**
   * Ensures the complete customer-facing system catalog exists.
   *
   * <p>The method keeps its historical name because new-company bootstrap depends on it. It returns
   * the FREE plan id, while also ensuring STARTER, GROWTH, and ENTERPRISE are present.
   */
  @Transactional
  public Long ensureFreePlan() {
    entitlementCatalogInitializer.ensureCatalog();
    validateSeedCatalog();

    Long freePlanId = null;
    for (SystemPlanSeed seed : SYSTEM_PLANS) {
      Long planId = ensurePlan(seed);
      if (FREE_PLAN_CODE.equals(seed.code())) {
        freePlanId = planId;
      }
    }
    if (freePlanId == null) {
      throw new IllegalStateException("System FREE plan was not seeded");
    }
    return freePlanId;
  }

  private Long ensurePlan(SystemPlanSeed seed) {
    List<Long> existing =
        jdbcTemplate.queryForList(
            """
            select id
            from plans
            where code = ? and version = ?
            limit 1
            """,
            Long.class,
            seed.code(),
            SYSTEM_PLAN_VERSION);

    Long planId;
    if (!existing.isEmpty()) {
      planId = existing.getFirst();
    } else {
      planId = IdGenerator.nextId();
      jdbcTemplate.update(
          """
          insert into plans
            (id, code, name, description, status, visibility, billing_cycle, base_price, currency,
             version, created_at, updated_at)
          values (?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', ?, ?, 'INR', ?, now(), now())
          on conflict (code, version) do nothing
          """,
          planId,
          seed.code(),
          seed.name(),
          seed.description(),
          seed.billingCycle(),
          seed.price(),
          SYSTEM_PLAN_VERSION);

      List<Long> loaded =
          jdbcTemplate.queryForList(
              "select id from plans where code = ? and version = ? limit 1",
              Long.class,
              seed.code(),
              SYSTEM_PLAN_VERSION);
      if (loaded.isEmpty()) {
        throw new IllegalStateException("Unable to create system plan " + seed.code());
      }
      planId = loaded.getFirst();
    }

    ensurePrices(planId, seed.prices());
    ensureEntitlements(planId, seed.entitlements());
    return planId;
  }

  private void ensurePrices(Long planId, List<SystemPriceSeed> prices) {
    for (SystemPriceSeed priceSeed : prices) {
      Integer count =
          jdbcTemplate.queryForObject(
              """
              select count(*)
              from plan_prices
              where plan_id = ?
                and billing_cycle = ?
                and amount = ?
                and currency = 'INR'
                and status = 'ACTIVE'
              """,
              Integer.class,
              planId,
              priceSeed.billingCycle(),
              priceSeed.amount());
      if (count != null && count > 0) {
        continue;
      }

      jdbcTemplate.update(
          """
          insert into plan_prices
            (id, plan_id, billing_cycle, amount, currency, effective_from, effective_to,
             status, created_at, updated_at)
          values (?, ?, ?, ?, 'INR', now(), null, 'ACTIVE', now(), now())
          """,
          IdGenerator.nextId(),
          planId,
          priceSeed.billingCycle(),
          priceSeed.amount());
    }
  }

  private void ensureEntitlements(Long planId, Map<String, String> entitlements) {
    for (Map.Entry<String, String> entitlement : entitlements.entrySet()) {
      jdbcTemplate.update(
          """
          insert into plan_entitlements
            (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
          values (?, ?, ?, ?, now(), now())
          on conflict (plan_id, entitlement_key) do update
            set entitlement_value = excluded.entitlement_value,
                updated_at = now()
          """,
          IdGenerator.nextId(),
          planId,
          entitlement.getKey(),
          entitlement.getValue());
    }
  }

  private void validateSeedCatalog() {
    Set<String> activeDefinitions =
        new LinkedHashSet<>(
            jdbcTemplate.queryForList(
                """
                select entitlement_key
                from entitlement_definitions
                where active = true
                order by entitlement_key
                """,
                String.class));

    for (SystemPlanSeed seed : SYSTEM_PLANS) {
      Set<String> missing = new LinkedHashSet<>(activeDefinitions);
      missing.removeAll(seed.entitlements().keySet());

      Set<String> unknown = new LinkedHashSet<>(seed.entitlements().keySet());
      unknown.removeAll(activeDefinitions);

      if (!missing.isEmpty() || !unknown.isEmpty()) {
        throw new IllegalStateException(
            seed.code()
                + " entitlement seed is out of sync with the active entitlement catalog"
                + " missing="
                + missing
                + " unknown="
                + unknown);
      }
    }

    assertMonotonic("FREE", FREE_ENTITLEMENTS, "STARTER", STARTER_ENTITLEMENTS);
    assertMonotonic("STARTER", STARTER_ENTITLEMENTS, "GROWTH", GROWTH_ENTITLEMENTS);
    assertMonotonic("GROWTH", GROWTH_ENTITLEMENTS, "ENTERPRISE", ENTERPRISE_ENTITLEMENTS);
  }

  private void assertMonotonic(
      String lowerName,
      Map<String, String> lower,
      String higherName,
      Map<String, String> higher) {
    for (String key : lower.keySet()) {
      String lowerValue = lower.get(key);
      String higherValue = higher.get(key);

      if ("true".equalsIgnoreCase(lowerValue) || "false".equalsIgnoreCase(lowerValue)) {
        if ("true".equalsIgnoreCase(lowerValue) && !"true".equalsIgnoreCase(higherValue)) {
          throw new IllegalStateException(
              higherName + " must not disable entitlement " + key + " enabled by " + lowerName);
        }
        continue;
      }

      BigDecimal lowerNumber = new BigDecimal(lowerValue);
      BigDecimal higherNumber = new BigDecimal(higherValue);
      if (higherNumber.compareTo(lowerNumber) < 0) {
        throw new IllegalStateException(
            higherName + " entitlement " + key + " must be >= " + lowerName);
      }
    }
  }

  private record SystemPriceSeed(String billingCycle, BigDecimal amount) {}

  private record SystemPlanSeed(
      String code,
      String name,
      String description,
      String billingCycle,
      BigDecimal price,
      List<SystemPriceSeed> prices,
      Map<String, String> entitlements) {}
}
