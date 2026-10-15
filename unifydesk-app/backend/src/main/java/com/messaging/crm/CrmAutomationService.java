package com.messaging.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmAutomationService {
  private static final Logger log = LoggerFactory.getLogger(CrmAutomationService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final CompanyMemberRepository memberRepository;
  private final CrmWorkService workService;

  public CrmAutomationService(
      JdbcTemplate jdbc,
      CompanyMemberRepository memberRepository,
      CrmWorkService workService) {
    this.jdbc = jdbc;
    this.memberRepository = memberRepository;
    this.workService = workService;
  }

  public record AutomationRule(
      String id,
      String companyId,
      String name,
      String description,
      boolean isEnabled,
      String triggerType,
      Map<String, Object> triggerConfig,
      String actionType,
      Map<String, Object> actionConfig,
      long executionCount,
      Instant lastExecutedAt,
      String createdByUserId,
      Instant createdAt,
      Instant updatedAt) {}

  public record AutomationLog(
      String id,
      String companyId,
      String ruleId,
      String ruleName,
      String triggerType,
      String entityType,
      String entityId,
      String actionType,
      String status,
      String details,
      Instant executedAt) {}

  public record SaveRuleInput(
      String name,
      String description,
      Boolean isEnabled,
      String triggerType,
      Map<String, Object> triggerConfig,
      String actionType,
      Map<String, Object> actionConfig) {}

  @Transactional(readOnly = true)
  public List<AutomationRule> listRules(long companyId) {
    return jdbc.query(
        """
        select * from crm_automation_rules
        where company_id = ?
        order by created_at desc
        """,
        (rs, rowNum) -> mapRule(rs),
        companyId);
  }

  @Transactional(readOnly = true)
  public AutomationRule getRule(long companyId, long ruleId) {
    List<AutomationRule> list = jdbc.query(
        """
        select * from crm_automation_rules
        where company_id = ? and id = ?
        """,
        (rs, rowNum) -> mapRule(rs),
        companyId,
        ruleId);
    if (list.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CRM_AUTOMATION_RULE_NOT_FOUND");
    }
    return list.getFirst();
  }

  @Transactional
  public AutomationRule createRule(long companyId, Long actorUserId, SaveRuleInput input) {
    if (input == null || input.name() == null || input.name().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_RULE_NAME_REQUIRED");
    }
    if (input.triggerType() == null || input.triggerType().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TRIGGER_TYPE_REQUIRED");
    }
    if (input.actionType() == null || input.actionType().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ACTION_TYPE_REQUIRED");
    }

    long id = IdGenerator.nextId();
    String triggerJson = toJson(input.triggerConfig() != null ? input.triggerConfig() : Map.of());
    String actionJson = toJson(input.actionConfig() != null ? input.actionConfig() : Map.of());
    boolean enabled = input.isEnabled() == null || input.isEnabled();

    jdbc.update(
        """
        insert into crm_automation_rules (
            id, company_id, name, description, is_enabled,
            trigger_type, trigger_config, action_type, action_config,
            execution_count, created_by_user_id, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, 0, ?, now(), now())
        """,
        id,
        companyId,
        input.name().trim(),
        input.description() != null ? input.description().trim() : null,
        enabled,
        input.triggerType().trim().toUpperCase(),
        triggerJson,
        input.actionType().trim().toUpperCase(),
        actionJson,
        actorUserId);

    return getRule(companyId, id);
  }

  @Transactional
  public AutomationRule updateRule(long companyId, long ruleId, SaveRuleInput input) {
    AutomationRule existing = getRule(companyId, ruleId);

    String name = input.name() != null && !input.name().trim().isEmpty() ? input.name().trim() : existing.name();
    String description = input.description() != null ? input.description().trim() : existing.description();
    boolean enabled = input.isEnabled() != null ? input.isEnabled() : existing.isEnabled();
    String triggerType = input.triggerType() != null ? input.triggerType().trim().toUpperCase() : existing.triggerType();
    String actionType = input.actionType() != null ? input.actionType().trim().toUpperCase() : existing.actionType();

    String triggerJson = toJson(input.triggerConfig() != null ? input.triggerConfig() : existing.triggerConfig());
    String actionJson = toJson(input.actionConfig() != null ? input.actionConfig() : existing.actionConfig());

    jdbc.update(
        """
        update crm_automation_rules
        set name = ?, description = ?, is_enabled = ?,
            trigger_type = ?, trigger_config = ?::jsonb,
            action_type = ?, action_config = ?::jsonb,
            updated_at = now()
        where company_id = ? and id = ?
        """,
        name,
        description,
        enabled,
        triggerType,
        triggerJson,
        actionType,
        actionJson,
        companyId,
        ruleId);

    return getRule(companyId, ruleId);
  }

  @Transactional
  public AutomationRule toggleRule(long companyId, long ruleId, boolean enabled) {
    getRule(companyId, ruleId);
    jdbc.update(
        """
        update crm_automation_rules
        set is_enabled = ?, updated_at = now()
        where company_id = ? and id = ?
        """,
        enabled,
        companyId,
        ruleId);
    return getRule(companyId, ruleId);
  }

  @Transactional
  public void deleteRule(long companyId, long ruleId) {
    int updated = jdbc.update(
        "delete from crm_automation_rules where company_id = ? and id = ?",
        companyId,
        ruleId);
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CRM_AUTOMATION_RULE_NOT_FOUND");
    }
  }

  @Transactional(readOnly = true)
  public List<AutomationLog> listLogs(long companyId, Long ruleId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    if (ruleId != null) {
      return jdbc.query(
          """
          select * from crm_automation_logs
          where company_id = ? and rule_id = ?
          order by executed_at desc
          limit ?
          """,
          (rs, rowNum) -> mapLog(rs),
          companyId,
          ruleId,
          safeLimit);
    }
    return jdbc.query(
        """
        select * from crm_automation_logs
        where company_id = ?
        order by executed_at desc
        limit ?
        """,
        (rs, rowNum) -> mapLog(rs),
        companyId,
        safeLimit);
  }

  /**
   * Main Event Trigger Dispatcher
   * Executes matching enabled rules for a trigger event asynchronously or inline with isolated failure catching.
   */
  public void fireEvent(
      long companyId,
      String triggerType,
      String entityType,
      String entityId,
      Map<String, Object> eventContext) {
    try {
      List<AutomationRule> rules = jdbc.query(
          """
          select * from crm_automation_rules
          where company_id = ? and is_enabled = true and trigger_type = ?
          order by created_at asc
          """,
          (rs, rowNum) -> mapRule(rs),
          companyId,
          triggerType);

      if (rules.isEmpty()) return;

      Map<String, Object> ctx = eventContext != null ? eventContext : Map.of();

      for (AutomationRule rule : rules) {
        try {
          if (evaluateTriggerCondition(rule, ctx)) {
            executeRuleAction(rule, entityType, entityId, ctx);
          }
        } catch (Exception ex) {
          log.error("Failed to execute CRM automation rule id={} name={}: {}", rule.id(), rule.name(), ex.getMessage(), ex);
          recordLog(
              companyId,
              Long.parseLong(rule.id()),
              rule.name(),
              triggerType,
              entityType,
              entityId,
              rule.actionType(),
              "FAILED",
              "Execution error: " + ex.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("Error evaluating CRM automation triggers for company={}: {}", companyId, e.getMessage(), e);
    }
  }

  private boolean evaluateTriggerCondition(AutomationRule rule, Map<String, Object> ctx) {
    Map<String, Object> conf = rule.triggerConfig();
    if (conf == null || conf.isEmpty()) return true;

    String triggerType = rule.triggerType();

    if ("LEAD_STAGE_CHANGED".equalsIgnoreCase(triggerType)) {
      Object toStage = conf.get("toStage");
      if (toStage != null && !toStage.toString().trim().isEmpty() && !"ALL".equalsIgnoreCase(toStage.toString())) {
        Object actualStage = ctx.get("toStage");
        if (actualStage == null || !toStage.toString().equalsIgnoreCase(actualStage.toString())) {
          return false;
        }
      }
    } else if ("DEAL_STAGE_CHANGED".equalsIgnoreCase(triggerType)) {
      Object toStage = conf.get("toStage");
      if (toStage != null && !toStage.toString().trim().isEmpty() && !"ALL".equalsIgnoreCase(toStage.toString())) {
        Object actualStage = ctx.get("toStage");
        if (actualStage == null || !toStage.toString().equalsIgnoreCase(actualStage.toString())) {
          return false;
        }
      }
    } else if ("FORM_SUBMITTED".equalsIgnoreCase(triggerType)) {
      Object formKey = conf.get("formKey");
      if (formKey != null && !formKey.toString().trim().isEmpty() && !"ALL".equalsIgnoreCase(formKey.toString())) {
        Object actualFormKey = ctx.get("formKey");
        if (actualFormKey == null || !formKey.toString().equalsIgnoreCase(actualFormKey.toString())) {
          return false;
        }
      }
    }

    return true;
  }

  private void executeRuleAction(
      AutomationRule rule,
      String entityType,
      String entityId,
      Map<String, Object> ctx) {
    long companyId = Long.parseLong(rule.companyId());
    long ruleId = Long.parseLong(rule.id());
    Map<String, Object> conf = rule.actionConfig() != null ? rule.actionConfig() : Map.of();
    String actionType = rule.actionType();

    String resultSummary = "";

    switch (actionType) {
      case "CREATE_TASK" -> {
        String titleTemplate = (String) conf.getOrDefault("taskTitle", "Follow up with prospect");
        String details = (String) conf.getOrDefault("taskDetails", "");
        String priority = (String) conf.getOrDefault("priority", "NORMAL");
        int dueDays = conf.get("dueDaysOffset") != null ? Integer.parseInt(conf.get("dueDaysOffset").toString()) : 1;
        String assigneeType = (String) conf.getOrDefault("assigneeType", "ROUND_ROBIN");
        String specificUserId = (String) conf.get("assigneeUserId");

        // Format Title with tokens
        String contactName = (String) ctx.getOrDefault("contactName", "Lead");
        String title = titleTemplate.replace("{contactName}", contactName)
            .replace("{stage}", String.valueOf(ctx.getOrDefault("toStage", "")))
            .replace("{dealName}", String.valueOf(ctx.getOrDefault("dealName", "")));

        Long assignee = resolveAssignee(companyId, assigneeType, specificUserId, ctx);
        if (assignee == null) {
          resultSummary = "Skipped task creation: No active company member available for assignment";
          recordLog(companyId, ruleId, rule.name(), rule.triggerType(), entityType, entityId, actionType, "SKIPPED", resultSummary);
          return;
        }

        Long contactId = null;
        if ("LEAD".equalsIgnoreCase(entityType) || "FORM_SUBMISSION".equalsIgnoreCase(entityType)) {
          try {
            contactId = Long.parseLong(entityId);
          } catch (Exception ignored) {}
        } else if (ctx.get("contactId") != null) {
          try {
            contactId = Long.parseLong(ctx.get("contactId").toString());
          } catch (Exception ignored) {}
        }

        Instant dueAt = Instant.now().plus(dueDays, ChronoUnit.DAYS);

        // System actor id = 0 or company owner
        long actor = assignee;
        workService.createTask(
            companyId,
            title,
            details,
            priority,
            dueAt,
            assignee,
            contactId,
            null,
            actor,
            "OPEN");

        resultSummary = "Created CRM task '" + title + "' assigned to user=" + assignee + " due=" + dueAt;
      }
      case "ASSIGN_OWNER" -> {
        String assigneeType = (String) conf.getOrDefault("assigneeType", "ROUND_ROBIN");
        String specificUserId = (String) conf.get("assigneeUserId");
        Long targetOwner = resolveAssignee(companyId, assigneeType, specificUserId, ctx);

        if (targetOwner == null) {
          resultSummary = "Skipped owner assignment: No active member found";
          recordLog(companyId, ruleId, rule.name(), rule.triggerType(), entityType, entityId, actionType, "SKIPPED", resultSummary);
          return;
        }

        Long contactId = null;
        if ("LEAD".equalsIgnoreCase(entityType) || "FORM_SUBMISSION".equalsIgnoreCase(entityType)) {
          try {
            contactId = Long.parseLong(entityId);
          } catch (Exception ignored) {}
        }

        if (contactId != null) {
          jdbc.update(
              "update contacts set owner_user_id = ?, updated_at = now() where company_id = ? and id = ?",
              targetOwner,
              companyId,
              contactId);
          resultSummary = "Assigned contact=" + contactId + " owner to user=" + targetOwner;
        } else {
          resultSummary = "Skipped: contactId not resolvable for entity=" + entityId;
        }
      }
      case "UPDATE_LEAD_STAGE" -> {
        String targetStage = (String) conf.get("targetStage");
        if (targetStage == null || targetStage.trim().isEmpty()) {
          resultSummary = "Skipped: targetStage not specified in rule configuration";
          recordLog(companyId, ruleId, rule.name(), rule.triggerType(), entityType, entityId, actionType, "SKIPPED", resultSummary);
          return;
        }

        Long contactId = null;
        if ("LEAD".equalsIgnoreCase(entityType) || "FORM_SUBMISSION".equalsIgnoreCase(entityType)) {
          try {
            contactId = Long.parseLong(entityId);
          } catch (Exception ignored) {}
        }

        if (contactId != null) {
          jdbc.update(
              "update crm_leads set stage = ?, updated_at = now() where company_id = ? and contact_id = ?",
              targetStage.trim().toUpperCase(),
              companyId,
              contactId);
          resultSummary = "Advanced lead=" + contactId + " stage to " + targetStage;
        } else {
          resultSummary = "Skipped: contactId not resolvable for entity=" + entityId;
        }
      }
      default -> resultSummary = "Action " + actionType + " executed successfully";
    }

    // Update rule counters
    jdbc.update(
        """
        update crm_automation_rules
        set execution_count = execution_count + 1, last_executed_at = now(), updated_at = now()
        where company_id = ? and id = ?
        """,
        companyId,
        ruleId);

    // Record audit log
    recordLog(companyId, ruleId, rule.name(), rule.triggerType(), entityType, entityId, actionType, "SUCCESS", resultSummary);
  }

  private Long resolveAssignee(
      long companyId,
      String assigneeType,
      String specificUserId,
      Map<String, Object> ctx) {
    if ("SPECIFIC_USER".equalsIgnoreCase(assigneeType) && specificUserId != null && !specificUserId.trim().isEmpty()) {
      try {
        return Long.parseLong(specificUserId.trim());
      } catch (Exception ignored) {}
    }

    if ("LEAD_OWNER".equalsIgnoreCase(assigneeType) && ctx.get("ownerUserId") != null) {
      try {
        return Long.parseLong(ctx.get("ownerUserId").toString());
      } catch (Exception ignored) {}
    }

    // Round-Robin among active company members
    List<CompanyMember> activeMembers = memberRepository.findByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE);
    if (activeMembers.isEmpty()) return null;

    // Fetch or initialize round-robin state
    List<Long> lastAssigned = jdbc.query(
        "select last_assigned_user_id from crm_round_robin_state where company_id = ?",
        (rs, rowNum) -> rs.getLong(1),
        companyId);

    Long lastUserId = lastAssigned.isEmpty() ? null : lastAssigned.getFirst();

    int nextIdx = 0;
    if (lastUserId != null) {
      for (int i = 0; i < activeMembers.size(); i++) {
        if (Objects.equals(activeMembers.get(i).getUser().getId(), lastUserId)) {
          nextIdx = (i + 1) % activeMembers.size();
          break;
        }
      }
    }

    Long chosenUserId = activeMembers.get(nextIdx).getUser().getId();

    // Update round-robin state
    jdbc.update(
        """
        insert into crm_round_robin_state (company_id, last_assigned_user_id, updated_at)
        values (?, ?, now())
        on conflict (company_id) do update
        set last_assigned_user_id = excluded.last_assigned_user_id, updated_at = now()
        """,
        companyId,
        chosenUserId);

    return chosenUserId;
  }

  private void recordLog(
      long companyId,
      long ruleId,
      String ruleName,
      String triggerType,
      String entityType,
      String entityId,
      String actionType,
      String status,
      String details) {
    long logId = IdGenerator.nextId();
    jdbc.update(
        """
        insert into crm_automation_logs (
            id, company_id, rule_id, rule_name, trigger_type,
            entity_type, entity_id, action_type, status, details, executed_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        """,
        logId,
        companyId,
        ruleId,
        ruleName,
        triggerType,
        entityType,
        entityId,
        actionType,
        status,
        details);
  }

  private AutomationRule mapRule(ResultSet rs) throws SQLException {
    return new AutomationRule(
        String.valueOf(rs.getLong("id")),
        String.valueOf(rs.getLong("company_id")),
        rs.getString("name"),
        rs.getString("description"),
        rs.getBoolean("is_enabled"),
        rs.getString("trigger_type"),
        fromJson(rs.getString("trigger_config")),
        rs.getString("action_type"),
        fromJson(rs.getString("action_config")),
        rs.getLong("execution_count"),
        toInstant(rs.getTimestamp("last_executed_at")),
        rs.getString("created_by_user_id"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private AutomationLog mapLog(ResultSet rs) throws SQLException {
    return new AutomationLog(
        String.valueOf(rs.getLong("id")),
        String.valueOf(rs.getLong("company_id")),
        String.valueOf(rs.getLong("rule_id")),
        rs.getString("rule_name"),
        rs.getString("trigger_type"),
        rs.getString("entity_type"),
        rs.getString("entity_id"),
        rs.getString("action_type"),
        rs.getString("status"),
        rs.getString("details"),
        toInstant(rs.getTimestamp("executed_at")));
  }

  private static Instant toInstant(Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }

  private static String toJson(Object obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (Exception e) {
      return "{}";
    }
  }

  private static Map<String, Object> fromJson(String json) {
    if (json == null || json.trim().isEmpty()) return new HashMap<>();
    try {
      return MAPPER.readValue(json, MAP_TYPE);
    } catch (Exception e) {
      return new HashMap<>();
    }
  }
}
