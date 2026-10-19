package com.messaging.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmLeadScoringService {

  private final JdbcTemplate jdbc;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};
  private static final TypeReference<List<ScoreBreakdownItem>> LIST_REF = new TypeReference<>() {};

  public record ScoreRuleDto(
      String id,
      String ruleName,
      String description,
      String ruleType,
      Map<String, Object> conditionConfig,
      int points,
      boolean isEnabled,
      int displayOrder,
      Instant createdAt,
      Instant updatedAt) {}

  public record ScoreSettingsDto(int hotThreshold, int warmThreshold, Instant updatedAt) {}

  public record ScoreBreakdownItem(
      String ruleId, String ruleName, String ruleType, int points, String reason) {}

  public record LeadScoreResult(
      String contactId,
      int score,
      String temperature, // 'HOT', 'WARM', 'COLD'
      int hotThreshold,
      int warmThreshold,
      List<ScoreBreakdownItem> breakdown,
      Instant scoredAt) {}

  public record SaveRuleInput(
      String ruleName,
      String description,
      String ruleType,
      Map<String, Object> conditionConfig,
      int points,
      Boolean isEnabled,
      Integer displayOrder) {}

  public record SaveSettingsInput(int hotThreshold, int warmThreshold) {}

  @Transactional
  public ScoreSettingsDto getSettings(long companyId) {
    ensureSettings(companyId);
    return jdbc.queryForObject(
        "select hot_threshold, warm_threshold, updated_at from crm_lead_score_settings where company_id = ?",
        (rs, row) ->
            new ScoreSettingsDto(
                rs.getInt("hot_threshold"),
                rs.getInt("warm_threshold"),
                rs.getTimestamp("updated_at").toInstant()),
        companyId);
  }

  @Transactional
  public ScoreSettingsDto updateSettings(long companyId, SaveSettingsInput input) {
    if (input.hotThreshold() <= input.warmThreshold()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "Hot threshold must be greater than warm threshold");
    }
    if (input.warmThreshold() < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Warm threshold cannot be negative");
    }
    jdbc.update(
        """
        insert into crm_lead_score_settings(company_id, hot_threshold, warm_threshold, updated_at)
        values(?, ?, ?, now())
        on conflict (company_id) do update
          set hot_threshold = excluded.hot_threshold,
              warm_threshold = excluded.warm_threshold,
              updated_at = now()
        """,
        companyId,
        input.hotThreshold(),
        input.warmThreshold());

    // Batch recalculate all lead scores with new thresholds
    recalculateAll(companyId);

    return getSettings(companyId);
  }

  @Transactional
  public List<ScoreRuleDto> getRules(long companyId) {
    seedDefaultRulesIfEmpty(companyId);
    return jdbc.query(
        """
        select id, rule_name, description, rule_type, condition_config, points,
               is_enabled, display_order, created_at, updated_at
        from crm_lead_score_rules
        where company_id = ?
        order by display_order asc, created_at asc
        """,
        this::mapRule,
        companyId);
  }

  @Transactional
  public ScoreRuleDto createRule(long companyId, SaveRuleInput input) {
    if (input.ruleName() == null || input.ruleName().trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rule name is required");
    }
    if (input.ruleType() == null || input.ruleType().trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rule type is required");
    }

    long key = IdGenerator.nextId();
    String condJson = "{}";
    try {
      if (input.conditionConfig() != null) {
        condJson = MAPPER.writeValueAsString(input.conditionConfig());
      }
    } catch (Exception ignored) {
    }

    int nextOrder =
        jdbc.queryForObject(
            "select coalesce(max(display_order), 0) + 1 from crm_lead_score_rules where company_id = ?",
            Integer.class,
            companyId);

    jdbc.update(
        """
        insert into crm_lead_score_rules(id, company_id, rule_name, description, rule_type, condition_config, points, is_enabled, display_order)
        values(?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
        """,
        key,
        companyId,
        input.ruleName().trim(),
        input.description() != null ? input.description().trim() : null,
        input.ruleType().trim().toUpperCase(),
        condJson,
        input.points(),
        input.isEnabled() != null ? input.isEnabled() : true,
        input.displayOrder() != null ? input.displayOrder() : nextOrder);

    recalculateAll(companyId);

    return getRule(companyId, key);
  }

  @Transactional
  public ScoreRuleDto updateRule(long companyId, long ruleId, SaveRuleInput input) {
    getRule(companyId, ruleId);
    String condJson = null;
    if (input.conditionConfig() != null) {
      try {
        condJson = MAPPER.writeValueAsString(input.conditionConfig());
      } catch (Exception ignored) {
      }
    }

    jdbc.update(
        """
        update crm_lead_score_rules
        set rule_name = coalesce(nullif(trim(?), ''), rule_name),
            description = coalesce(?, description),
            rule_type = coalesce(nullif(trim(?), ''), rule_type),
            condition_config = case when ? is not null then cast(? as jsonb) else condition_config end,
            points = coalesce(?, points),
            is_enabled = coalesce(?, is_enabled),
            display_order = coalesce(?, display_order),
            updated_at = now()
        where company_id = ? and id = ?
        """,
        input.ruleName(),
        input.description(),
        input.ruleType() != null ? input.ruleType().trim().toUpperCase() : null,
        condJson,
        condJson,
        input.points(),
        input.isEnabled(),
        input.displayOrder(),
        companyId,
        ruleId);

    recalculateAll(companyId);

    return getRule(companyId, ruleId);
  }

  @Transactional
  public void deleteRule(long companyId, long ruleId) {
    int rows =
        jdbc.update(
            "delete from crm_lead_score_rules where company_id = ? and id = ?", companyId, ruleId);
    if (rows == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Score rule not found");
    }
    recalculateAll(companyId);
  }

  @Transactional
  public ScoreRuleDto toggleRule(long companyId, long ruleId, boolean enabled) {
    getRule(companyId, ruleId);
    jdbc.update(
        "update crm_lead_score_rules set is_enabled = ?, updated_at = now() where company_id = ? and id = ?",
        enabled,
        companyId,
        ruleId);
    recalculateAll(companyId);
    return getRule(companyId, ruleId);
  }

  @Transactional(readOnly = true)
  public ScoreRuleDto getRule(long companyId, long ruleId) {
    List<ScoreRuleDto> list =
        jdbc.query(
            """
            select id, rule_name, description, rule_type, condition_config, points,
                   is_enabled, display_order, created_at, updated_at
            from crm_lead_score_rules
            where company_id = ? and id = ?
            """,
            this::mapRule,
            companyId,
            ruleId);
    if (list.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Score rule not found");
    }
    return list.getFirst();
  }

  /**
   * Calculates score for a single lead, persists the result on crm_leads, and returns the result.
   */
  @Transactional
  public LeadScoreResult calculateScore(long companyId, long contactId) {
    ScoreSettingsDto settings = getSettings(companyId);
    List<ScoreRuleDto> rules = getRules(companyId);

    // 1. Fetch Contact info
    record ContactScoreData(String organization, long phoneCount, long emailCount) {}
    List<ContactScoreData> contactRows =
        jdbc.query(
            """
            select c.organization,
                   (select count(*) from contact_identities ci
                    where ci.company_id = c.company_id and ci.contact_id = c.id
                      and ci.identity_type = 'PHONE' and ci.status = 'ACTIVE') as phone_count,
                   (select count(*) from contact_identities ci
                    where ci.company_id = c.company_id and ci.contact_id = c.id
                      and ci.identity_type = 'EMAIL' and ci.status = 'ACTIVE') as email_count
            from contacts c
            where c.company_id = ? and c.id = ? and c.status = 'ACTIVE'
            """,
            (rs, row) ->
                new ContactScoreData(
                    rs.getString("organization"),
                    rs.getLong("phone_count"),
                    rs.getLong("email_count")),
            companyId,
            contactId);

    if (contactRows.isEmpty()) {
      return new LeadScoreResult(
          String.valueOf(contactId),
          0,
          "COLD",
          settings.hotThreshold(),
          settings.warmThreshold(),
          List.of(),
          Instant.now());
    }

    ContactScoreData contactData = contactRows.getFirst();
    String organization = contactData.organization();
    long phoneCount = contactData.phoneCount();
    long emailCount = contactData.emailCount();

    // 2. Fetch Lead info
    record LeadRecord(String stage, String customFields) {}
    List<LeadRecord> leadRows =
        jdbc.query(
            "select stage, custom_fields from crm_leads where company_id = ? and contact_id = ?",
            (rs, row) -> new LeadRecord(rs.getString("stage"), rs.getString("custom_fields")),
            companyId,
            contactId);

    if (leadRows.isEmpty()) {
      return new LeadScoreResult(
          String.valueOf(contactId),
          0,
          "COLD",
          settings.hotThreshold(),
          settings.warmThreshold(),
          List.of(),
          Instant.now());
    }

    String stage = leadRows.getFirst().stage();
    String rawCustom = leadRows.getFirst().customFields();
    Map<String, Object> customFields = Map.of();
    if (rawCustom != null && !rawCustom.isBlank()) {
      try {
        customFields = MAPPER.readValue(rawCustom, MAP_REF);
      } catch (Exception ignored) {
      }
    }

    // 3. Additional activity checks (form submissions, deals, notes)
    long formSubmissions = 0L;
    try {
      Long cnt =
          jdbc.queryForObject(
              "select count(*) from crm_form_submissions where company_id = ? and contact_id = ?",
              Long.class,
              companyId,
              contactId);
      if (cnt != null) formSubmissions = cnt;
    } catch (Exception ignored) {
    }

    long dealsCount = 0L;
    try {
      Long cnt =
          jdbc.queryForObject(
              "select count(*) from crm_deals where company_id = ? and contact_id = ?",
              Long.class,
              companyId,
              contactId);
      if (cnt != null) dealsCount = cnt;
    } catch (Exception ignored) {
    }

    long notesCount = 0L;
    try {
      Long cnt =
          jdbc.queryForObject(
              "select count(*) from crm_notes where company_id = ? and contact_id = ?",
              Long.class,
              companyId,
              contactId);
      if (cnt != null) notesCount = cnt;
    } catch (Exception ignored) {
    }

    // 4. Evaluate rules
    int totalScore = 0;
    List<ScoreBreakdownItem> breakdown = new ArrayList<>();

    for (ScoreRuleDto rule : rules) {
      if (!rule.isEnabled()) continue;

      boolean matched = false;
      String reason = "";

      switch (rule.ruleType()) {
        case "HAS_PHONE" -> {
          if (phoneCount > 0) {
            matched = true;
            reason = "Active phone identity present";
          }
        }
        case "HAS_EMAIL" -> {
          if (emailCount > 0) {
            matched = true;
            reason = "Active email identity present";
          }
        }
        case "HAS_ORGANIZATION" -> {
          if (organization != null && !organization.trim().isBlank()) {
            matched = true;
            reason = "Organization / Company provided (" + organization.trim() + ")";
          }
        }
        case "STAGE_MATCH" -> {
          Object targetStageObj =
              rule.conditionConfig() != null ? rule.conditionConfig().get("stage") : null;
          String targetStage = targetStageObj != null ? targetStageObj.toString() : null;
          if (targetStage != null && targetStage.equalsIgnoreCase(stage)) {
            matched = true;
            reason = "Lead stage is " + stage;
          }
        }
        case "FORM_SUBMISSION" -> {
          if (formSubmissions > 0) {
            matched = true;
            reason = formSubmissions + " Web lead form submission(s)";
          }
        }
        case "HAS_DEAL" -> {
          if (dealsCount > 0) {
            matched = true;
            reason = dealsCount + " linked sales opportunity deal(s)";
          }
        }
        case "COMMUNICATION_ACTIVITY" -> {
          if (notesCount > 0) {
            matched = true;
            reason = notesCount + " conversation / CRM note entries logged";
          }
        }
        case "CUSTOM_FIELD_MATCH" -> {
          if (rule.conditionConfig() != null) {
            Object fkObj = rule.conditionConfig().get("fieldKey");
            String fieldKey = fkObj != null ? fkObj.toString() : null;
            Object opObj = rule.conditionConfig().getOrDefault("operator", "EXISTS");
            String op = opObj != null ? opObj.toString() : "EXISTS";
            Object val = rule.conditionConfig().get("value");

            if (fieldKey != null && customFields.containsKey(fieldKey)) {
              Object currentVal = customFields.get(fieldKey);
              if ("EXISTS".equalsIgnoreCase(op)) {
                if (currentVal != null && !String.valueOf(currentVal).isBlank()) {
                  matched = true;
                  reason = "Custom attribute '" + fieldKey + "' is present";
                }
              } else if ("EQUALS".equalsIgnoreCase(op)) {
                if (currentVal != null
                    && String.valueOf(currentVal).equalsIgnoreCase(String.valueOf(val))) {
                  matched = true;
                  reason = "Custom attribute '" + fieldKey + "' equals '" + val + "'";
                }
              } else if ("GTE".equalsIgnoreCase(op)) {
                try {
                  double num = Double.parseDouble(String.valueOf(currentVal));
                  double threshold = Double.parseDouble(String.valueOf(val));
                  if (num >= threshold) {
                    matched = true;
                    reason = "Custom attribute '" + fieldKey + "' (" + num + ") >= " + threshold;
                  }
                } catch (Exception ignored) {
                }
              }
            }
          }
        }
      }

      if (matched) {
        totalScore += rule.points();
        breakdown.add(
            new ScoreBreakdownItem(
                rule.id(), rule.ruleName(), rule.ruleType(), rule.points(), reason));
      }
    }

    // Floor at 0 for clean display
    int finalScore = Math.max(0, totalScore);

    String temperature;
    if (finalScore >= settings.hotThreshold()) {
      temperature = "HOT";
    } else if (finalScore >= settings.warmThreshold()) {
      temperature = "WARM";
    } else {
      temperature = "COLD";
    }

    String breakdownJson = "[]";
    try {
      breakdownJson = MAPPER.writeValueAsString(breakdown);
    } catch (Exception ignored) {
    }

    // Update crm_leads with score and breakdown
    jdbc.update(
        """
        update crm_leads
        set score = ?, temperature = ?, score_breakdown = cast(? as jsonb), scored_at = now()
        where company_id = ? and contact_id = ?
        """,
        finalScore,
        temperature,
        breakdownJson,
        companyId,
        contactId);

    return new LeadScoreResult(
        String.valueOf(contactId),
        finalScore,
        temperature,
        settings.hotThreshold(),
        settings.warmThreshold(),
        breakdown,
        Instant.now());
  }

  /** Recalculates scores for all leads in the company. */
  @Transactional
  public int recalculateAll(long companyId) {
    List<Long> contactIds =
        jdbc.queryForList(
            "select contact_id from crm_leads where company_id = ?", Long.class, companyId);

    int count = 0;
    for (Long cid : contactIds) {
      calculateScore(companyId, cid);
      count++;
    }
    return count;
  }

  private void ensureSettings(long companyId) {
    jdbc.update(
        """
        insert into crm_lead_score_settings(company_id, hot_threshold, warm_threshold)
        values(?, 70, 30)
        on conflict (company_id) do nothing
        """,
        companyId);
  }

  private void seedDefaultRulesIfEmpty(long companyId) {
    ensureSettings(companyId);

    Integer count =
        jdbc.queryForObject(
            "select count(*) from crm_lead_score_rules where company_id = ?",
            Integer.class,
            companyId);

    if (count != null && count > 0) return;

    // Seed standard rules
    insertSeedRule(
        companyId,
        "Has phone number",
        "Contact has an active phone number",
        "HAS_PHONE",
        Map.of(),
        15,
        1);
    insertSeedRule(
        companyId,
        "Has email address",
        "Contact has an active email address",
        "HAS_EMAIL",
        Map.of(),
        10,
        2);
    insertSeedRule(
        companyId,
        "Organization specified",
        "Contact organization or company is provided",
        "HAS_ORGANIZATION",
        Map.of(),
        15,
        3);
    insertSeedRule(
        companyId,
        "Stage is Qualified",
        "Lead has been advanced to QUALIFIED stage",
        "STAGE_MATCH",
        Map.of("stage", "QUALIFIED"),
        30,
        4);
    insertSeedRule(
        companyId,
        "Stage is Contacted",
        "Lead has been reached out to",
        "STAGE_MATCH",
        Map.of("stage", "CONTACTED"),
        10,
        5);
    insertSeedRule(
        companyId,
        "Stage is Unqualified",
        "Lead marked unqualified",
        "STAGE_MATCH",
        Map.of("stage", "UNQUALIFIED"),
        -50,
        6);
    insertSeedRule(
        companyId,
        "Web form submission",
        "Contact submitted an inbound web lead form",
        "FORM_SUBMISSION",
        Map.of(),
        25,
        7);
    insertSeedRule(
        companyId,
        "Has sales deal",
        "Contact is associated with an active deal",
        "HAS_DEAL",
        Map.of(),
        20,
        8);
    insertSeedRule(
        companyId,
        "Has interaction / notes",
        "Recorded communication notes or WhatsApp interactions",
        "COMMUNICATION_ACTIVITY",
        Map.of(),
        10,
        9);
  }

  private void insertSeedRule(
      long companyId,
      String name,
      String desc,
      String type,
      Map<String, Object> config,
      int points,
      int order) {
    long key = IdGenerator.nextId();
    String condJson = "{}";
    try {
      condJson = MAPPER.writeValueAsString(config);
    } catch (Exception ignored) {
    }

    jdbc.update(
        """
        insert into crm_lead_score_rules(id, company_id, rule_name, description, rule_type, condition_config, points, is_enabled, display_order)
        values(?, ?, ?, ?, ?, cast(? as jsonb), ?, true, ?)
        """,
        key,
        companyId,
        name,
        desc,
        type,
        condJson,
        points,
        order);
  }

  private ScoreRuleDto mapRule(ResultSet rs, int row) throws SQLException {
    Map<String, Object> config = Map.of();
    String raw = rs.getString("condition_config");
    if (raw != null && !raw.isBlank()) {
      try {
        config = MAPPER.readValue(raw, MAP_REF);
      } catch (Exception ignored) {
      }
    }

    return new ScoreRuleDto(
        String.valueOf(rs.getLong("id")),
        rs.getString("rule_name"),
        rs.getString("description"),
        rs.getString("rule_type"),
        config,
        rs.getInt("points"),
        rs.getBoolean("is_enabled"),
        rs.getInt("display_order"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
