package com.messaging.billing.bootstrap;

import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(10)
public class SystemEntitlementCatalogInitializer implements ApplicationRunner {

  private static final List<Definition> DEFINITIONS =
      List.of(
          new Definition(910000000000000001L, "MAX_WORKSPACE_USERS", "Workspace users", "Maximum active users in the workspace, including the owner", EntitlementValueType.INTEGER, EntitlementCategory.WORKSPACE, "1"),
          new Definition(910000000000000002L, "CONTACT_LIMIT", "Contacts", "Maximum contacts stored in the workspace", EntitlementValueType.INTEGER, EntitlementCategory.WORKSPACE, "0"),
          new Definition(910000000000000003L, "SEGMENT_LIMIT", "Segments", "Maximum audience segments in the workspace", EntitlementValueType.INTEGER, EntitlementCategory.WORKSPACE, "0"),
          new Definition(910000000000000004L, "BROADCAST_GROUP_LIMIT", "Broadcast groups", "Maximum reusable broadcast recipient groups", EntitlementValueType.INTEGER, EntitlementCategory.WORKSPACE, "0"),
          new Definition(910000000000000005L, "EMAIL_ACCOUNTS", "Email connections", "Maximum connected email accounts", EntitlementValueType.INTEGER, EntitlementCategory.CHANNEL, "0"),
          new Definition(910000000000000006L, "WHATSAPP_ACCOUNTS", "WhatsApp connections", "Maximum connected WhatsApp Business accounts", EntitlementValueType.INTEGER, EntitlementCategory.CHANNEL, "0"),
          new Definition(910000000000000007L, "WHATSAPP_NUMBERS", "WhatsApp numbers", "Maximum WhatsApp sender phone numbers", EntitlementValueType.INTEGER, EntitlementCategory.CHANNEL, "0"),
          new Definition(910000000000000008L, "SMS_ACCOUNTS", "SMS connections", "Maximum connected SMS provider accounts", EntitlementValueType.INTEGER, EntitlementCategory.CHANNEL, "0"),
          new Definition(910000000000000009L, "SMS_SENDER_IDS", "SMS sender IDs", "Maximum SMS sender IDs or headers", EntitlementValueType.INTEGER, EntitlementCategory.CHANNEL, "0"),
          new Definition(910000000000000010L, "BROADCAST_BATCH_LIMIT", "Recipients per broadcast", "Maximum recipients in a single broadcast", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000011L, "BROADCASTS_PER_DAY", "Broadcasts per day", "Maximum broadcasts sent per calendar day", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000012L, "BROADCASTS_PER_MONTH", "Broadcasts per month", "Maximum broadcasts sent per billing month", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000013L, "BROADCAST_RECIPIENTS_PER_DAY", "Broadcast recipients per day", "Maximum total broadcast recipients reached per calendar day", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000014L, "BROADCAST_RECIPIENTS_PER_MONTH", "Broadcast recipients per month", "Maximum total broadcast recipients reached per billing month", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000015L, "SCHEDULED_BROADCASTS_ENABLED", "Scheduled broadcasts", "Allow broadcasts to be scheduled for later delivery", EntitlementValueType.BOOLEAN, EntitlementCategory.BROADCAST, "false"),
          new Definition(910000000000000016L, "EMAIL_TEMPLATE_LIMIT", "Email templates", "Maximum saved email templates", EntitlementValueType.INTEGER, EntitlementCategory.TEMPLATE, "0"),
          new Definition(910000000000000017L, "WHATSAPP_TEMPLATE_LIMIT", "WhatsApp templates", "Maximum saved WhatsApp templates managed by the workspace", EntitlementValueType.INTEGER, EntitlementCategory.TEMPLATE, "0"),
          new Definition(910000000000000018L, "SMS_TEMPLATE_LIMIT", "SMS templates", "Maximum saved SMS templates", EntitlementValueType.INTEGER, EntitlementCategory.TEMPLATE, "0"),
          new Definition(910000000000000019L, "STORAGE_GB", "Storage", "Maximum private workspace storage in GiB; use -1 for unlimited", EntitlementValueType.DECIMAL, EntitlementCategory.STORAGE, "0"),
          new Definition(910000000000000020L, "AUTO_REPLY_ENABLED", "Auto reply", "Enable automated replies", EntitlementValueType.BOOLEAN, EntitlementCategory.FEATURE, "false"),
          new Definition(910000000000000021L, "USER_SIGNATURE_ENABLED", "User signatures", "Enable per-user message signatures", EntitlementValueType.BOOLEAN, EntitlementCategory.FEATURE, "false"),
          new Definition(910000000000000022L, "API_ACCESS_ENABLED", "API access", "Enable customer API access", EntitlementValueType.BOOLEAN, EntitlementCategory.FEATURE, "false"),
          new Definition(910000000000000023L, "MANAGED_EMAIL_ENABLED", "Managed Email", "Enable UnifyDesk Managed Email for the workspace", EntitlementValueType.BOOLEAN, EntitlementCategory.FEATURE, "false"),
          new Definition(910000000000000024L, "MANAGED_EMAIL_DOMAINS", "Managed Email domains", "Maximum active or provisioning Managed Email domains", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000025L, "MANAGED_EMAIL_SENDERS", "Managed Email senders", "Maximum Managed Email sender identities", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000026L, "MANAGED_EMAIL_MESSAGES_MONTHLY", "Managed Email messages per month", "Maximum provider-accepted UnifyDesk Managed Email messages per UTC calendar month", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"),
          new Definition(910000000000000027L, "MANAGED_SMS_SEGMENTS_MONTHLY", "Managed SMS segments per month", "Maximum provider-accepted UnifyDesk Managed SMS segments per UTC calendar month", EntitlementValueType.INTEGER, EntitlementCategory.BROADCAST, "0"));

  private static final String UPSERT_SQL =
      """
      insert into entitlement_definitions
        (id, entitlement_key, name, description, value_type, category, default_value,
         active, created_at, updated_at)
      values (?, ?, ?, ?, ?, ?, ?, true, now(), now())
      on conflict (entitlement_key) do nothing
      """;

  private final JdbcTemplate jdbcTemplate;

  public SystemEntitlementCatalogInitializer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    ensureCatalog();
  }

  public void ensureCatalog() {
    Set<String> existing =
        new HashSet<>(
            jdbcTemplate.queryForList(
                "select entitlement_key from entitlement_definitions", String.class));
    int restored = 0;
    for (Definition definition : DEFINITIONS) {
      if (existing.contains(definition.key())) continue;
      restored +=
          jdbcTemplate.update(
              UPSERT_SQL,
              definition.id(),
              definition.key(),
              definition.name(),
              definition.description(),
              definition.valueType().name(),
              definition.category().name(),
              definition.defaultValue());
    }
    log.info(
        "system_entitlement_catalog_synced definitions={} affectedRows={}",
        DEFINITIONS.size(),
        restored);
  }

  private record Definition(
      long id,
      String key,
      String name,
      String description,
      EntitlementValueType valueType,
      EntitlementCategory category,
      String defaultValue) {}
}
