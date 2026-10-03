package com.messaging.billing.bootstrap;

import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemBillingBootstrapService {

  public static final String FREE_PLAN_CODE = "FREE";
  public static final int FREE_PLAN_VERSION = 1;

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

    if (existing.isEmpty()) {
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
    }

    if (existing.isEmpty()) {
      throw new IllegalStateException("Unable to create system FREE plan");
    }

    Long planId = existing.getFirst();

    // Published commercial versions are immutable. Bootstrap may create missing seed state,
    // but it must never rewrite an existing published plan on application restart.
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
    List<Map<String, Object>> definitions =
        jdbcTemplate.queryForList(
            """
            select entitlement_key, default_value
            from entitlement_definitions
            where active = true
            order by entitlement_key
            """);

    for (Map<String, Object> definition : definitions) {
      String key = String.valueOf(definition.get("entitlement_key"));
      String value = String.valueOf(definition.get("default_value"));
      jdbcTemplate.update(
          """
          insert into plan_entitlements
            (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
          values (?, ?, ?, ?, now(), now())
          on conflict (plan_id, entitlement_key) do nothing
          """,
          IdGenerator.nextId(),
          planId,
          key,
          value);
    }
  }
}
