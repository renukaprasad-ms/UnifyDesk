package com.messaging.billing.bootstrap;

import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
  public static final int FREE_PLAN_VERSION = 2;

  /**
   * Canonical system FREE plan entitlements.
   *
   * <p>FREE is a bootstrap-owned commercial version. When these defaults change materially, publish
   * a new FREE version instead of rewriting an already-published version.
   */
  private static final Map<String, String> FREE_PLAN_ENTITLEMENTS =
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

  private final JdbcTemplate jdbcTemplate;
  private final SystemEntitlementCatalogInitializer entitlementCatalogInitializer;

  @Transactional
  public Long ensureFreePlan() {
    entitlementCatalogInitializer.ensureCatalog();

    List<Long> existing =
        jdbcTemplate.queryForList(
            """
            select id
            from plans
            where code = ? and version = ?
            limit 1
            """,
            Long.class,
            FREE_PLAN_CODE,
            FREE_PLAN_VERSION);

    if (!existing.isEmpty()) {
      // Published commercial versions are immutable. Startup must never rewrite price,
      // entitlements, visibility, or other commercial terms of an existing version.
      return existing.getFirst();
    }

    jdbcTemplate.update(
        """
        insert into plans
          (id, code, name, description, status, billing_cycle, base_price, currency,
           version, created_at, updated_at)
        values (?, ?, 'Free', 'Default system plan for new workspaces', 'PUBLISHED',
                'FREE', 0, 'INR', ?, now(), now())
        on conflict (code, version) do nothing
        """,
        IdGenerator.nextId(),
        FREE_PLAN_CODE,
        FREE_PLAN_VERSION);
    existing =
        jdbcTemplate.queryForList(
            "select id from plans where code = ? and version = ? limit 1",
            Long.class,
            FREE_PLAN_CODE,
            FREE_PLAN_VERSION);

    if (existing.isEmpty()) {
      throw new IllegalStateException("Unable to create system FREE plan");
    }

    Long planId = existing.getFirst();
    ensureFreePrice(planId);
    ensureDefaultEntitlements(planId);
    return planId;
  }

  private void ensureFreePrice(Long planId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from plan_prices
            where plan_id = ?
              and billing_cycle = 'FREE'
              and amount = 0
              and status = 'ACTIVE'
            """,
            Integer.class,
            planId);
    if (count != null && count > 0) return;

    jdbcTemplate.update(
        """
        insert into plan_prices
          (id, plan_id, billing_cycle, amount, currency, effective_from, effective_to,
           status, created_at, updated_at)
        values (?, ?, 'FREE', 0, 'INR', now(), null, 'ACTIVE', now(), now())
        """,
        IdGenerator.nextId(),
        planId);
  }

  private void ensureDefaultEntitlements(Long planId) {
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

    Set<String> missingFromFreePlan = new LinkedHashSet<>(activeDefinitions);
    missingFromFreePlan.removeAll(FREE_PLAN_ENTITLEMENTS.keySet());

    Set<String> unknownFreePlanKeys = new LinkedHashSet<>(FREE_PLAN_ENTITLEMENTS.keySet());
    unknownFreePlanKeys.removeAll(activeDefinitions);

    if (!missingFromFreePlan.isEmpty() || !unknownFreePlanKeys.isEmpty()) {
      throw new IllegalStateException(
          "FREE plan entitlement seed is out of sync with the active entitlement catalog"
              + " missing="
              + missingFromFreePlan
              + " unknown="
              + unknownFreePlanKeys);
    }

    for (Map.Entry<String, String> entitlement : FREE_PLAN_ENTITLEMENTS.entrySet()) {
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
}
