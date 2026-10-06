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
          Map.entry("MAX_WORKSPACE_USERS", "3"),
          Map.entry("CONTACT_LIMIT", "1000"),
          Map.entry("SEGMENT_LIMIT", "25"),
          Map.entry("BROADCAST_GROUP_LIMIT", "25"),
          Map.entry("EMAIL_ACCOUNTS", "1"),
          Map.entry("WHATSAPP_ACCOUNTS", "1"),
          Map.entry("WHATSAPP_NUMBERS", "1"),
          Map.entry("SMS_ACCOUNTS", "1"),
          Map.entry("SMS_SENDER_IDS", "1"),
          Map.entry("BROADCAST_BATCH_LIMIT", "500"),
          Map.entry("BROADCASTS_PER_DAY", "25"),
          Map.entry("BROADCASTS_PER_MONTH", "250"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "1000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "10000"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "false"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "25"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "25"),
          Map.entry("SMS_TEMPLATE_LIMIT", "25"),
          Map.entry("STORAGE_GB", "1"),
          Map.entry("AUTO_REPLY_ENABLED", "false"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "false"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "1"),
          Map.entry("MANAGED_EMAIL_SENDERS", "3"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "10000"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "10000"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "25"),
          Map.entry("CONTACT_LIST_LIMIT", "50"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "5000"));

  private static final Map<String, String> STARTER_ENTITLEMENTS =
      Map.ofEntries(
          Map.entry("MAX_WORKSPACE_USERS", "5"),
          Map.entry("CONTACT_LIMIT", "5000"),
          Map.entry("SEGMENT_LIMIT", "100"),
          Map.entry("BROADCAST_GROUP_LIMIT", "100"),
          Map.entry("EMAIL_ACCOUNTS", "2"),
          Map.entry("WHATSAPP_ACCOUNTS", "2"),
          Map.entry("WHATSAPP_NUMBERS", "2"),
          Map.entry("SMS_ACCOUNTS", "2"),
          Map.entry("SMS_SENDER_IDS", "2"),
          Map.entry("BROADCAST_BATCH_LIMIT", "2000"),
          Map.entry("BROADCASTS_PER_DAY", "100"),
          Map.entry("BROADCASTS_PER_MONTH", "1000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "10000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "100000"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "true"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "100"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "100"),
          Map.entry("SMS_TEMPLATE_LIMIT", "100"),
          Map.entry("STORAGE_GB", "5"),
          Map.entry("AUTO_REPLY_ENABLED", "true"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "false"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "3"),
          Map.entry("MANAGED_EMAIL_SENDERS", "10"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "50000"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "50000"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "100"),
          Map.entry("CONTACT_LIST_LIMIT", "200"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "20000"));

  private static final Map<String, String> GROWTH_ENTITLEMENTS =
      Map.ofEntries(
          Map.entry("MAX_WORKSPACE_USERS", "15"),
          Map.entry("CONTACT_LIMIT", "25000"),
          Map.entry("SEGMENT_LIMIT", "500"),
          Map.entry("BROADCAST_GROUP_LIMIT", "500"),
          Map.entry("EMAIL_ACCOUNTS", "5"),
          Map.entry("WHATSAPP_ACCOUNTS", "5"),
          Map.entry("WHATSAPP_NUMBERS", "5"),
          Map.entry("SMS_ACCOUNTS", "5"),
          Map.entry("SMS_SENDER_IDS", "5"),
          Map.entry("BROADCAST_BATCH_LIMIT", "10000"),
          Map.entry("BROADCASTS_PER_DAY", "500"),
          Map.entry("BROADCASTS_PER_MONTH", "5000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_DAY", "50000"),
          Map.entry("BROADCAST_RECIPIENTS_PER_MONTH", "500000"),
          Map.entry("SCHEDULED_BROADCASTS_ENABLED", "true"),
          Map.entry("EMAIL_TEMPLATE_LIMIT", "500"),
          Map.entry("WHATSAPP_TEMPLATE_LIMIT", "500"),
          Map.entry("SMS_TEMPLATE_LIMIT", "500"),
          Map.entry("STORAGE_GB", "25"),
          Map.entry("AUTO_REPLY_ENABLED", "true"),
          Map.entry("USER_SIGNATURE_ENABLED", "true"),
          Map.entry("API_ACCESS_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_ENABLED", "true"),
          Map.entry("MANAGED_EMAIL_DOMAINS", "10"),
          Map.entry("MANAGED_EMAIL_SENDERS", "25"),
          Map.entry("MANAGED_EMAIL_MESSAGES_MONTHLY", "250000"),
          Map.entry("MANAGED_SMS_SEGMENTS_MONTHLY", "250000"),
          Map.entry("CONTACT_CUSTOM_FIELD_LIMIT", "250"),
          Map.entry("CONTACT_LIST_LIMIT", "1000"),
          Map.entry("CONTACT_IMPORT_ROWS_LIMIT", "100000"));

  private static final List<SystemPlanSeed> SYSTEM_PLANS =
      List.of(
          new SystemPlanSeed(
              "FREE",
              "Free",
              "Get started with core UnifyDesk workspace features",
              "FREE",
              BigDecimal.ZERO,
              FREE_ENTITLEMENTS),
          new SystemPlanSeed(
              "STARTER",
              "Starter",
              "For small teams starting customer communication at scale",
              "MONTHLY",
              new BigDecimal("499"),
              STARTER_ENTITLEMENTS),
          new SystemPlanSeed(
              "GROWTH",
              "Growth",
              "For growing teams with higher limits and advanced capabilities",
              "MONTHLY",
              new BigDecimal("999"),
              GROWTH_ENTITLEMENTS));

  private final JdbcTemplate jdbcTemplate;
  private final SystemEntitlementCatalogInitializer entitlementCatalogInitializer;

  /**
   * Ensures the complete customer-facing system catalog exists.
   *
   * <p>The method keeps its historical name because new-company bootstrap depends on it. It returns
   * the FREE plan id, while also ensuring STARTER and GROWTH are present.
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

    if (!existing.isEmpty()) {
      // Published commercial versions are immutable. Never rewrite an existing seed version.
      return existing.getFirst();
    }

    jdbcTemplate.update(
        """
        insert into plans
          (id, code, name, description, status, visibility, billing_cycle, base_price, currency,
           version, created_at, updated_at)
        values (?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', ?, ?, 'INR', ?, now(), now())
        on conflict (code, version) do nothing
        """,
        IdGenerator.nextId(),
        seed.code(),
        seed.name(),
        seed.description(),
        seed.billingCycle(),
        seed.price(),
        SYSTEM_PLAN_VERSION);

    existing =
        jdbcTemplate.queryForList(
            "select id from plans where code = ? and version = ? limit 1",
            Long.class,
            seed.code(),
            SYSTEM_PLAN_VERSION);
    if (existing.isEmpty()) {
      throw new IllegalStateException("Unable to create system plan " + seed.code());
    }

    Long planId = existing.getFirst();
    ensurePrice(planId, seed);
    ensureEntitlements(planId, seed.entitlements());
    return planId;
  }

  private void ensurePrice(Long planId, SystemPlanSeed seed) {
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
            seed.billingCycle(),
            seed.price());
    if (count != null && count > 0) {
      return;
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
        seed.billingCycle(),
        seed.price());
  }

  private void ensureEntitlements(Long planId, Map<String, String> entitlements) {
    for (Map.Entry<String, String> entitlement : entitlements.entrySet()) {
      jdbcTemplate.update(
          """
          insert into plan_entitlements
            (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
          values (?, ?, ?, ?, now(), now())
          on conflict (plan_id, entitlement_key) do nothing
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

  private record SystemPlanSeed(
      String code,
      String name,
      String description,
      String billingCycle,
      BigDecimal price,
      Map<String, String> entitlements) {}
}
