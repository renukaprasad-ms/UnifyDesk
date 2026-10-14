package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmDealsService {
  private final JdbcTemplate jdbc;
  private final CrmCustomFieldService crmCustomFields;
  private final CrmPipelineService pipelineService;

  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();
  private static final com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>> MAP_REF =
      new com.fasterxml.jackson.core.type.TypeReference<>() {};

  @org.springframework.beans.factory.annotation.Autowired
  public CrmDealsService(
      JdbcTemplate jdbc,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmCustomFieldService crmCustomFields,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmPipelineService pipelineService) {
    this.jdbc = jdbc;
    this.crmCustomFields = crmCustomFields;
    this.pipelineService = pipelineService != null ? pipelineService : new CrmPipelineService(jdbc);
  }

  public CrmDealsService(JdbcTemplate jdbc) {
    this(jdbc, null, null);
  }

  public enum Stage {
    NEW,
    QUALIFIED,
    PROPOSAL,
    NEGOTIATION,
    WON,
    LOST
  }

  public record Deal(
      String id,
      String title,
      String description,
      String stage,
      String amount,
      String currency,
      LocalDate expectedCloseDate,
      String ownerUserId,
      String ownerName,
      String contactId,
      String contactName,
      String accountId,
      String accountName,
      String pipelineId,
      String pipelineName,
      java.util.Map<String, Object> customFields,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record Input(
      String title,
      String description,
      String amount,
      String currency,
      LocalDate expectedCloseDate,
      Long ownerUserId,
      Long contactId,
      Long accountId,
      Long pipelineId,
      String stage,
      java.util.Map<String, Object> customFields) {

    public Input(
        String title,
        String description,
        String amount,
        String currency,
        LocalDate expectedCloseDate,
        Long ownerUserId,
        Long contactId,
        Long accountId,
        java.util.Map<String, Object> customFields) {
      this(
          title,
          description,
          amount,
          currency,
          expectedCloseDate,
          ownerUserId,
          contactId,
          accountId,
          null,
          null,
          customFields);
    }

    public Input(
        String title,
        String description,
        String amount,
        String currency,
        LocalDate expectedCloseDate,
        Long ownerUserId,
        Long contactId,
        Long accountId) {
      this(
          title,
          description,
          amount,
          currency,
          expectedCloseDate,
          ownerUserId,
          contactId,
          accountId,
          null,
          null,
          java.util.Map.of());
    }
  }

  public record Page(List<Deal> items, String nextCursor, boolean hasMore) {}

  public record Event(
      String id,
      String eventType,
      String previousStage,
      String newStage,
      String changedFields,
      String actorName,
      String actorUsername,
      Instant occurredAt) {}

  private static final String SELECT =
      """
      select d.*, u.name as owner_name, c.display_name as contact_name, a.name as account_name,
             p.name as pipeline_name
      from crm_deals d
      left join users u on u.id = d.owner_user_id
      left join contacts c on c.company_id = d.company_id and c.id = d.contact_id
      left join crm_accounts a on a.company_id = d.company_id and a.id = d.account_id
      left join crm_pipelines p on p.company_id = d.company_id and p.id = d.pipeline_id
      """;

  private static String id(ResultSet rs, String field) throws SQLException {
    Object value = rs.getObject(field);
    return value == null ? null : value.toString();
  }

  private static Deal map(ResultSet rs, int row) throws SQLException {
    var close = rs.getDate("expected_close_date");
    java.util.Map<String, Object> customFields = java.util.Map.of();
    String rawCustom = rs.getString("custom_fields");
    if (rawCustom != null && !rawCustom.isBlank()) {
      try {
        customFields = MAPPER.readValue(rawCustom, MAP_REF);
      } catch (Exception ignored) {}
    }
    return new Deal(
        id(rs, "id"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("stage"),
        rs.getBigDecimal("amount").stripTrailingZeros().toPlainString(),
        rs.getString("currency"),
        close == null ? null : close.toLocalDate(),
        id(rs, "owner_user_id"),
        rs.getString("owner_name"),
        id(rs, "contact_id"),
        rs.getString("contact_name"),
        id(rs, "account_id"),
        rs.getString("account_name"),
        id(rs, "pipeline_id"),
        rs.getString("pipeline_name"),
        customFields,
        rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  @Transactional(readOnly = true)
  public Deal get(long companyId, long dealId) {
    var rows =
        jdbc.query(
            SELECT + " where d.company_id=? and d.id=?", CrmDealsService::map, companyId, dealId);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_DEAL_NOT_FOUND");
    return rows.getFirst();
  }

  @Transactional(readOnly = true)
  public Page list(long companyId, Long pipelineId, String stage, Long before, String query, int size) {
    if (before != null && before <= 0) throw invalid();
    String search = query == null ? "" : query.trim();
    if (search.length() > 100) throw invalid();
    int limit = Math.max(1, Math.min(size, 100));
    var rows =
        jdbc.query(
            SELECT
                + """
        where d.company_id = ?
          and (cast(? as bigint) is null or d.pipeline_id = ?)
          and (cast(? as varchar) is null or d.stage = ?)
          and (cast(? as bigint) is null or d.id < ?)
          and position(lower(?) in lower(d.title)) > 0
        order by d.id desc limit ?
        """,
            CrmDealsService::map,
            companyId,
            pipelineId,
            pipelineId,
            stage,
            stage,
            before,
            before,
            search,
            limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return new Page(items, more ? items.getLast().id() : null, more);
  }

  @Transactional(readOnly = true)
  public Page list(long companyId, Stage stage, Long before, String query, int size) {
    return list(companyId, null, stage == null ? null : stage.name(), before, query, size);
  }

  @Transactional
  public Deal create(long companyId, Input input, long actor) {
    BigDecimal amount = validate(companyId, input);
    long owner = input.ownerUserId() == null ? actor : input.ownerUserId();
    requireOwner(companyId, owner);

    long pipelineId =
        input.pipelineId() != null
            ? input.pipelineId()
            : pipelineService.ensureDefaultPipeline(companyId);

    String stage = input.stage() != null && !input.stage().isBlank() ? input.stage().trim() : "NEW";

    java.util.Map<String, Object> cleanCustom =
        crmCustomFields != null
            ? crmCustomFields.validateValues(
                companyId, CrmCustomFieldService.EntityType.DEAL, input.customFields())
            : input.customFields() != null ? input.customFields() : java.util.Map.of();
    String customJson;
    try {
      customJson = MAPPER.writeValueAsString(cleanCustom);
    } catch (Exception e) {
      customJson = "{}";
    }

    long key = IdGenerator.nextId();
    jdbc.update(
        """
        insert into crm_deals(id, company_id, pipeline_id, stage, title, description, amount, currency,
          expected_close_date, owner_user_id, contact_id, account_id, custom_fields, created_by_user_id)
        values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
        """,
        key,
        companyId,
        pipelineId,
        stage,
        input.title().trim(),
        text(input.description()),
        amount,
        input.currency(),
        input.expectedCloseDate(),
        owner,
        input.contactId(),
        input.accountId(),
        customJson,
        actor);

    event(companyId, key, "CREATED", null, stage, null, actor);
    return get(companyId, key);
  }

  @Transactional
  public Deal update(long companyId, long dealId, long version, Input input, long actor) {
    Deal previous = get(companyId, dealId);
    BigDecimal amount = validate(companyId, input);
    if (input.ownerUserId() != null) requireOwner(companyId, input.ownerUserId());

    long pipelineId =
        input.pipelineId() != null
            ? input.pipelineId()
            : (previous.pipelineId() != null ? Long.parseLong(previous.pipelineId()) : pipelineService.ensureDefaultPipeline(companyId));

    java.util.Map<String, Object> customToSave =
        input.customFields() != null
            ? (crmCustomFields != null
                ? crmCustomFields.validateValues(
                    companyId, CrmCustomFieldService.EntityType.DEAL, input.customFields())
                : input.customFields())
            : (previous.customFields() != null ? previous.customFields() : java.util.Map.of());
    String customJson;
    try {
      customJson = MAPPER.writeValueAsString(customToSave);
    } catch (Exception e) {
      customJson = "{}";
    }

    int count =
        jdbc.update(
            """
        update crm_deals set title=?, description=?, amount=?, currency=?, expected_close_date=?,
          owner_user_id=?, contact_id=?, account_id=?, pipeline_id=?, custom_fields=cast(? as jsonb),
          version=version+1, updated_at=now()
        where company_id=? and id=? and version=?
        """,
            input.title().trim(),
            text(input.description()),
            amount,
            input.currency(),
            input.expectedCloseDate(),
            input.ownerUserId(),
            input.contactId(),
            input.accountId(),
            pipelineId,
            customJson,
            companyId,
            dealId,
            version);
    if (count != 1) throw conflict();
    Deal result = get(companyId, dealId);
    List<String> changed = new ArrayList<>();
    if (!previous.title().equals(result.title())) changed.add("Title");
    if (!Objects.equals(previous.description(), result.description())) changed.add("Description");
    if (!previous.amount().equals(result.amount())
        || !previous.currency().equals(result.currency())) changed.add("Value");
    if (!Objects.equals(previous.expectedCloseDate(), result.expectedCloseDate()))
      changed.add("Close date");
    if (!Objects.equals(previous.ownerUserId(), result.ownerUserId())) changed.add("Owner");
    if (!Objects.equals(previous.contactId(), result.contactId())) changed.add("Contact");
    if (!Objects.equals(previous.accountId(), result.accountId())) changed.add("Company");
    if (!Objects.equals(previous.pipelineId(), result.pipelineId())) changed.add("Pipeline");
    if (!changed.isEmpty())
      event(
          companyId,
          dealId,
          "UPDATED",
          previous.stage(),
          result.stage(),
          String.join(", ", changed),
          actor);
    return result;
  }

  @Transactional
  public Deal move(long companyId, long dealId, long version, String stage, long actor) {
    Deal previous = get(companyId, dealId);
    if (previous.version() != version) throw conflict();
    if (previous.stage().equalsIgnoreCase(stage)) return previous;

    // Check if stage category is WON or LOST
    List<String> categories =
        jdbc.query(
            """
            select s.category from crm_pipeline_stages s
            join crm_deals d on d.company_id = s.company_id and d.pipeline_id = s.pipeline_id
            where d.company_id = ? and d.id = ? and s.key = ?
            """,
            (rs, row) -> rs.getString("category"),
            companyId,
            dealId,
            stage);
    String cat = !categories.isEmpty() ? categories.getFirst() : stage;
    boolean isClosed = "WON".equalsIgnoreCase(cat) || "LOST".equalsIgnoreCase(cat);

    int count =
        jdbc.update(
            """
        update crm_deals set stage = ?, closed_at = case when ? then now() else null end,
          version = version + 1, updated_at = now()
        where company_id = ? and id = ? and version = ?
        """,
            stage,
            isClosed,
            companyId,
            dealId,
            version);
    if (count != 1) throw conflict();
    event(
        companyId, dealId, "STAGE_CHANGED", previous.stage(), stage, "Stage", actor);
    return get(companyId, dealId);
  }

  @Transactional
  public Deal move(long companyId, long dealId, long version, Stage stage, long actor) {
    return move(companyId, dealId, version, stage.name(), actor);
  }

  @Transactional(readOnly = true)
  public List<Event> history(long companyId, long dealId) {
    get(companyId, dealId);
    return jdbc.query(
        """
        select e.*,u.name as actor_name,u.username as actor_username from crm_deal_events e
        left join users u on u.id=e.actor_user_id where e.company_id=? and e.deal_id=?
        order by e.occurred_at desc,e.id desc limit 100
        """,
        (rs, row) ->
            new Event(
                id(rs, "id"),
                rs.getString("event_type"),
                rs.getString("previous_stage"),
                rs.getString("new_stage"),
                rs.getString("changed_fields"),
                rs.getString("actor_name"),
                rs.getString("actor_username"),
                rs.getTimestamp("occurred_at").toInstant()),
        companyId,
        dealId);
  }

  private BigDecimal validate(long companyId, Input input) {
    if (input.amount() == null
        || input.title() == null
        || input.title().isBlank()
        || input.title().trim().length() > 240
        || (input.description() != null && input.description().length() > 4000)) throw invalid();
    BigDecimal amount;
    try {
      if (input.currency() == null || !input.currency().matches("[A-Z]{3}")) throw invalid();
      int scale = Currency.getInstance(input.currency()).getDefaultFractionDigits();
      if (scale < 0 || scale > 3) throw invalid();
      amount = new BigDecimal(input.amount()).setScale(scale, RoundingMode.UNNECESSARY);
      if (amount.signum() < 0 || amount.compareTo(new BigDecimal("99999999999999999")) > 0)
        throw invalid();
    } catch (Exception e) {
      throw invalid();
    }
    if (input.contactId() != null
        && jdbc.queryForList(
                "select 1 from contacts where company_id=? and id=?", companyId, input.contactId())
            .isEmpty()) throw invalid();
    if (input.accountId() != null
        && jdbc.queryForList(
                "select 1 from crm_accounts where company_id=? and id=?",
                companyId,
                input.accountId())
            .isEmpty()) throw invalid();
    return amount;
  }

  private void requireOwner(long companyId, long ownerUserId) {
    var rows =
        jdbc.queryForList(
            "select 1 from company_members where company_id=? and user_id=? and status='ACTIVE'",
            companyId,
            ownerUserId);
    if (rows.isEmpty()) throw invalid();
  }

  private void event(
      long companyId,
      long dealId,
      String type,
      String previous,
      String next,
      String changed,
      long actor) {
    jdbc.update(
        """
        insert into crm_deal_events(id,company_id,deal_id,event_type,previous_stage,new_stage,changed_fields,actor_user_id)
        values(?,?,?,?,?,?,?,?)
        """,
        IdGenerator.nextId(),
        companyId,
        dealId,
        type,
        previous,
        next,
        changed,
        actor);
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.BAD_REQUEST, "CRM_DEAL_INVALID");
  }

  private static ApiException conflict() {
    return new ApiException(HttpStatus.CONFLICT, "CRM_DEAL_CONFLICT");
  }
}
