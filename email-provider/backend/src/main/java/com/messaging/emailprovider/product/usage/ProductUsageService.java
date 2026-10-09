package com.messaging.emailprovider.product.usage;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product's independent, immutable Postfix handoff usage ledger and reports. */
@Service
public class ProductUsageService {
  private final JdbcTemplate jdbc;
  private final EmailProjectRepository projects;
  private final EmailMessageRepository messages;

  public ProductUsageService(JdbcTemplate jdbc, EmailProjectRepository projects,
      EmailMessageRepository messages) {
    this.jdbc = jdbc;
    this.projects = projects;
    this.messages = messages;
  }

  @Transactional
  public void recordHandoff(EmailRecipient recipient) {
    var message = messages.findById(recipient.getMessageId()).orElse(null);
    if (message == null || !projects.existsById(message.getCompanyId())) return;
    // One billable unit per unique recipient handoff, even on Kafka replay.
    jdbc.update("""
      INSERT INTO email_product_usage_entries
        (id,recipient_id,project_id,message_id,unit_price_micro_inr,
         occurred_at,created_at,updated_at)
      VALUES (?, ?, ?, ?,
        COALESCE((SELECT price_micro_inr_per_recipient FROM email_product_rates
                  WHERE project_id=?),0),
        ?, now(), now())
      ON CONFLICT (recipient_id) DO NOTHING
      """,
      com.messaging.emailprovider.common.id.IdGenerator.nextId(),
      recipient.getId(), message.getCompanyId(), message.getId(),
      message.getCompanyId(), Instant.now());
  }

  public record Usage(String projectId, LocalDate from, LocalDate to,
      long handedOff, long chargedMicroInr, String currency) {}

  /**
   * Signed-id cursor paging for internal reconciliation. This returns immutable Postfix
   * handoff records, not final delivery signals, and not central UnifyDesk invoice lines.
   * String IDs retain precision when transferred to JavaScript dashboards.
   */
  public record HandoffEntry(
      String usageId, String recipientId, String messageId, Instant occurredAt,
      long productPriceMicroInr) {}

  public record HandoffPage(
      String projectId, List<HandoffEntry> entries, String nextAfterId, boolean hasMore) {}

  @Transactional(readOnly = true)
  public String latestUsageId(Long projectId) {
    if (projectId == null || projectId <= 0 || !projects.existsById(projectId)) {
      throw new NotFoundException("Email project not found");
    }
    Long latest = jdbc.queryForObject(
        "select coalesce(max(id),0) from email_product_usage_entries where project_id = ?",
        Long.class, projectId);
    return Long.toString(latest == null ? 0L : latest);
  }

  @Transactional(readOnly = true)
  public HandoffPage handoffs(Long projectId, long afterId, int limit) {
    if (projectId == null || projectId <= 0 || !projects.existsById(projectId)) {
      throw new NotFoundException("Email project not found");
    }
    if (afterId < 0 || limit < 1 || limit > 500) {
      throw new BadRequestException("Invalid usage cursor or page size");
    }
    List<HandoffEntry> page = jdbc.query("""
        select id, recipient_id, message_id, occurred_at, unit_price_micro_inr
          from email_product_usage_entries
         where project_id = ? and id > ?
         order by id asc
         limit ?
        """,
        (rs, row) -> new HandoffEntry(
            Long.toString(rs.getLong("id")), Long.toString(rs.getLong("recipient_id")),
            Long.toString(rs.getLong("message_id")),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getLong("unit_price_micro_inr")),
        projectId, afterId, limit + 1);
    boolean more = page.size() > limit;
    List<HandoffEntry> entries = more ? page.subList(0, limit) : page;
    String cursor = entries.isEmpty()
        ? Long.toString(afterId) : entries.getLast().usageId();
    return new HandoffPage(projectId.toString(), List.copyOf(entries), cursor, more);
  }

  @Transactional(readOnly = true)
  public Usage report(Long projectId, LocalDate from, LocalDate to) {
    if (!projects.existsById(projectId)) throw new NotFoundException("Project not found");
    if (from == null || to == null || from.isAfter(to) || from.plusDays(366).isBefore(to)) {
      throw new BadRequestException("Usage range must be 1 to 366 days");
    }
    Instant fromAt = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toAt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return jdbc.queryForObject("""
      SELECT COUNT(*), COALESCE(SUM(unit_price_micro_inr),0)
      FROM email_product_usage_entries
      WHERE project_id=? AND occurred_at>=? AND occurred_at<?
      """,
      (rs,row)->new Usage(projectId.toString(), from, to,
          rs.getLong(1), rs.getLong(2), "INR"), projectId, fromAt, toAt);
  }

  @Transactional
  public void setRate(Long projectId, long unitPriceMicroInr) {
    if (!projects.existsById(projectId)) throw new NotFoundException("Project not found");
    if (unitPriceMicroInr < 0 || unitPriceMicroInr > 10_000_000L) {
      throw new BadRequestException("Per-recipient price outside configured safety range");
    }
    jdbc.update("""
      INSERT INTO email_product_rates (project_id,price_micro_inr_per_recipient,updated_at)
      VALUES (?,?,now())
      ON CONFLICT(project_id) DO UPDATE
      SET price_micro_inr_per_recipient=EXCLUDED.price_micro_inr_per_recipient,
          updated_at=now()
      """, projectId, unitPriceMicroInr);
  }
}
