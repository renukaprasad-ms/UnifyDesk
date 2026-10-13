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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmDealsService {
  private final JdbcTemplate jdbc;

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
      Stage stage,
      String amount,
      String currency,
      LocalDate expectedCloseDate,
      String ownerUserId,
      String ownerName,
      String contactId,
      String contactName,
      String accountId,
      String accountName,
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
      Long accountId) {}

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
      select d.*, u.name as owner_name,c.display_name as contact_name,a.name as account_name
      from crm_deals d left join users u on u.id=d.owner_user_id
      left join contacts c on c.company_id=d.company_id and c.id=d.contact_id
      left join crm_accounts a on a.company_id=d.company_id and a.id=d.account_id
      """;

  private static String id(ResultSet rs, String field) throws SQLException {
    Object value = rs.getObject(field);
    return value == null ? null : value.toString();
  }

  private static Deal map(ResultSet rs, int row) throws SQLException {
    var close = rs.getDate("expected_close_date");
    return new Deal(
        id(rs, "id"),
        rs.getString("title"),
        rs.getString("description"),
        Stage.valueOf(rs.getString("stage")),
        rs.getBigDecimal("amount").stripTrailingZeros().toPlainString(),
        rs.getString("currency"),
        close == null ? null : close.toLocalDate(),
        id(rs, "owner_user_id"),
        rs.getString("owner_name"),
        id(rs, "contact_id"),
        rs.getString("contact_name"),
        id(rs, "account_id"),
        rs.getString("account_name"),
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
  public Page list(long companyId, Stage stage, Long before, String query, int size) {
    if (before != null && before <= 0) throw invalid();
    String search = query == null ? "" : query.trim();
    if (search.length() > 100) throw invalid();
    int limit = Math.max(1, Math.min(size, 100));
    var rows =
        jdbc.query(
            SELECT
                + """
        where d.company_id=? and (cast(? as varchar) is null or d.stage=?)
          and (cast(? as bigint) is null or d.id<?) and position(lower(?) in lower(d.title))>0
        order by d.id desc limit ?
        """,
            CrmDealsService::map,
            companyId,
            stage == null ? null : stage.name(),
            stage == null ? null : stage.name(),
            before,
            before,
            search,
            limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return new Page(items, more ? items.getLast().id() : null, more);
  }

  @Transactional
  public Deal create(long companyId, Input input, long actor) {
    BigDecimal amount = validate(companyId, input);
    long owner = input.ownerUserId() == null ? actor : input.ownerUserId();
    requireOwner(companyId, owner);
    long key = IdGenerator.nextId();
    jdbc.update(
        """
        insert into crm_deals(id,company_id,title,description,amount,currency,expected_close_date,
          owner_user_id,contact_id,account_id,created_by_user_id) values(?,?,?,?,?,?,?,?,?,?,?)
        """,
        key,
        companyId,
        input.title().trim(),
        text(input.description()),
        amount,
        input.currency(),
        input.expectedCloseDate(),
        owner,
        input.contactId(),
        input.accountId(),
        actor);
    event(companyId, key, "CREATED", null, "NEW", null, actor);
    return get(companyId, key);
  }

  @Transactional
  public Deal update(long companyId, long dealId, long version, Input input, long actor) {
    Deal previous = get(companyId, dealId);
    BigDecimal amount = validate(companyId, input);
    if (input.ownerUserId() != null) requireOwner(companyId, input.ownerUserId());
    int count =
        jdbc.update(
            """
        update crm_deals set title=?,description=?,amount=?,currency=?,expected_close_date=?,
          owner_user_id=?,contact_id=?,account_id=?,version=version+1,updated_at=now()
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
    if (!changed.isEmpty())
      event(
          companyId,
          dealId,
          "UPDATED",
          previous.stage().name(),
          result.stage().name(),
          String.join(", ", changed),
          actor);
    return result;
  }

  @Transactional
  public Deal move(long companyId, long dealId, long version, Stage stage, long actor) {
    Deal previous = get(companyId, dealId);
    if (previous.version() != version) throw conflict();
    if (previous.stage() == stage) return previous;
    int count =
        jdbc.update(
            """
        update crm_deals set stage=?,closed_at=case when ? in ('WON','LOST') then now() else null end,
          version=version+1,updated_at=now() where company_id=? and id=? and version=?
        """,
            stage.name(),
            stage.name(),
            companyId,
            dealId,
            version);
    if (count != 1) throw conflict();
    event(
        companyId, dealId, "STAGE_CHANGED", previous.stage().name(), stage.name(), "Stage", actor);
    return get(companyId, dealId);
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
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw invalid();
    }
    if (input.expectedCloseDate() != null
        && (input.expectedCloseDate().getYear() < 1900
            || input.expectedCloseDate().getYear() > 9999)) throw invalid();
    if (input.contactId() != null
        && jdbc.queryForObject(
                "select count(*) from contacts where company_id=? and id=? and status='ACTIVE'",
                Long.class,
                companyId,
                input.contactId())
            != 1) throw invalid();
    if (input.accountId() != null
        && jdbc.queryForObject(
                "select count(*) from crm_accounts where company_id=? and id=?",
                Long.class,
                companyId,
                input.accountId())
            != 1) throw invalid();
    return amount;
  }

  private void requireOwner(long companyId, long owner) {
    var members =
        jdbc.queryForList(
            """
        select m.user_id from company_members m join users u on u.id=m.user_id
        where m.company_id=? and m.user_id=? and m.status='ACTIVE' and u.status='ACTIVE'
        for share of m,u
        """,
            Long.class,
            companyId,
            owner);
    if (members.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_DEAL_OWNER_INVALID");
  }

  private void event(
      long company,
      long deal,
      String type,
      String before,
      String after,
      String fields,
      long actor) {
    jdbc.update(
        "insert into crm_deal_events(id,company_id,deal_id,event_type,previous_stage,new_stage,changed_fields,actor_user_id) values(?,?,?,?,?,?,?,?)",
        IdGenerator.nextId(),
        company,
        deal,
        type,
        before,
        after,
        fields,
        actor);
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.BAD_REQUEST, "CRM_DEAL_INVALID_INPUT");
  }

  private static ApiException conflict() {
    return new ApiException(HttpStatus.CONFLICT, "CRM_DEAL_VERSION_CONFLICT");
  }
}
