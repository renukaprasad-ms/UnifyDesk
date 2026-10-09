package com.messaging.billing.mailbridge;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.company.repository.CompanyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bridges separate Mail product handoff events to PLATFORM OPERATIONAL REPORTING only.
 * Never records usage_events, creates invoices, or copies external product prices as
 * central billed amounts: doing so would risk charging an independent Mail customer twice.
 */
@Service
public class MailProjectBillingBridgeService {
  public record Link(
      String mailProjectId, String companyId, String mode, String state,
      Instant effectiveFrom, String lastExternalUsageId, Instant lastSyncedAt,
      String ownershipProof) {}

  public record SyncResult(String mailProjectId, String companyId, int imported,
                           String nextAfterId, boolean hasMore) {}

  private final JdbcTemplate jdbc;
  private final CompanyRepository companies;
  private final MailProductUsageClient mail;
  private final TransactionTemplate tx;
  private final boolean enabled;
  private final int batchSize;

  public MailProjectBillingBridgeService(
      JdbcTemplate jdbc, CompanyRepository companies, MailProductUsageClient mail,
      PlatformTransactionManager manager,
      @Value("${app.billing.mail-bridge.enabled:false}") boolean enabled,
      @Value("${app.billing.mail-bridge.batch-size:100}") int batchSize) {
    this.jdbc = jdbc;
    this.companies = companies;
    this.mail = mail;
    this.tx = new TransactionTemplate(manager);
    this.enabled = enabled;
    this.batchSize = Math.max(1, Math.min(500, batchSize));
  }

  public boolean isEnabled() {
    return enabled;
  }

  private void requireEnabled() {
    if (!enabled) throw new ServiceUnavailableException(
        "Mail usage bridge is disabled; enable explicit internal service credentials first");
  }

  public List<Link> links() {
    return jdbc.query("""
        select mail_project_id, company_id, mode, state, effective_from,
               last_external_usage_id, last_synced_at, ownership_proof
          from billing_mail_project_links order by created_at desc, mail_project_id
        """, (rs, index) -> map(rs));
  }

  public Link link(Long companyId, Long mailProjectId, Long actorId,
                   String ownershipProof, boolean verifiedByOperator) {
    requireEnabled();
    if (!verifiedByOperator || companyId == null || companyId <= 0
        || mailProjectId == null || mailProjectId <= 0
        || actorId == null || actorId <= 0
        || ownershipProof == null || ownershipProof.trim().length() < 12
        || ownershipProof.length() > 500) {
      throw new BadRequestException("Company, Mail project and verified ownership proof are required");
    }
    if (!companies.existsById(companyId)) {
      throw new NotFoundException("UnifyDesk company does not exist");
    }
    // Snapshot the remote project's latest immutable usage ID. Link is forward-only:
    // existing Mail activity is not retroactively imported or billed.
    long head = mail.head(mailProjectId);
    Instant linkedAt = Instant.now();
    try {
      return tx.execute(status -> {
        jdbc.update("""
            insert into billing_mail_project_links
              (company_id, mail_project_id, mode, state, ownership_proof,
               effective_from, last_external_usage_id, created_by_user_id)
            values (?, ?, 'REPORT_ONLY', 'ACTIVE', ?, ?, ?, ?)
            """, companyId, mailProjectId, ownershipProof.trim(),
            Timestamp.from(linkedAt), head, actorId);
        return requireLink(mailProjectId);
      });
    } catch (DataIntegrityViolationException duplicate) {
      throw new ConflictException("Mail project already linked to a platform company");
    }
  }

  public Link setState(Long mailProjectId, String state) {
    if (!"ACTIVE".equals(state) && !"PAUSED".equals(state)) {
      throw new BadRequestException("Invalid Mail linkage state");
    }
    return tx.execute(status -> {
      int changed = jdbc.update("""
          update billing_mail_project_links set state = ?, updated_at = now()
           where mail_project_id = ?
          """, state, mailProjectId);
      if (changed != 1) throw new NotFoundException("Mail project link not found");
      return requireLink(mailProjectId);
    });
  }

  public SyncResult sync(Long mailProjectId) {
    requireEnabled();
    Link initial = requireLink(mailProjectId);
    if (!"ACTIVE".equals(initial.state())) {
      throw new ConflictException("Mail project link is paused");
    }
    // Remote HTTP must occur OUTSIDE the database write transaction.
    long cursor = Long.parseLong(initial.lastExternalUsageId());
    MailProductUsageClient.Page page = mail.handoffs(mailProjectId, cursor, batchSize);
    if (page.projectId() != mailProjectId) {
      throw new ConflictException("Mail project ownership changed during usage sync");
    }
    return tx.execute(status -> {
      List<Link> locked = jdbc.query("""
          select mail_project_id, company_id, mode, state, effective_from,
                 last_external_usage_id, last_synced_at, ownership_proof
            from billing_mail_project_links
           where mail_project_id = ? for update
          """, (rs, idx) -> map(rs), mailProjectId);
      if (locked.isEmpty()) throw new NotFoundException("Mail project link not found");
      Link link = locked.getFirst();
      if (!"ACTIVE".equals(link.state())
          || !link.companyId().equals(initial.companyId())
          || !link.lastExternalUsageId().equals(initial.lastExternalUsageId())) {
        throw new ConflictException("Mail project link changed; retry synchronization");
      }
      int imported = 0;
      for (MailProductUsageClient.Handoff entry : page.entries()) {
        if (entry.occurredAt().isBefore(link.effectiveFrom())) continue;
        imported += jdbc.update("""
            insert into billing_mail_usage_imports
              (mail_project_id, remote_usage_id, company_id,
               remote_recipient_id, remote_message_id, occurred_at,
               product_price_micro_inr)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """, mailProjectId, entry.id(), Long.valueOf(link.companyId()),
            entry.recipientId(), entry.messageId(), Timestamp.from(entry.occurredAt()),
            entry.productPriceMicroInr());
      }
      jdbc.update("""
          update billing_mail_project_links
             set last_external_usage_id = ?, last_synced_at = now(), updated_at = now()
           where mail_project_id = ?
          """, page.nextAfterId(), mailProjectId);
      return new SyncResult(link.mailProjectId(), link.companyId(), imported,
          Long.toString(page.nextAfterId()), page.hasMore());
    });
  }

  @Scheduled(
      fixedDelayString = "${app.billing.mail-bridge.sync-interval-ms:300000}",
      initialDelayString = "${app.billing.mail-bridge.sync-initial-delay-ms:60000}")
  public void scheduledSync() {
    if (!enabled) return;
    List<Long> projects = jdbc.queryForList("""
        select mail_project_id from billing_mail_project_links
         where state = 'ACTIVE'
         order by coalesce(last_synced_at, created_at), mail_project_id
         limit 25
        """, Long.class);
    for (Long project : projects) {
      try {
        sync(project);
      } catch (RuntimeException exception) {
        // One Mail project must not prevent other companies from being synchronized.
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Mail usage sync failed for project {}", project, exception);
      }
    }
  }

  private Link requireLink(Long projectId) {
    List<Link> matches = jdbc.query("""
        select mail_project_id, company_id, mode, state, effective_from,
               last_external_usage_id, last_synced_at, ownership_proof
          from billing_mail_project_links where mail_project_id = ?
        """, (rs, idx) -> map(rs), projectId);
    if (matches.isEmpty()) throw new NotFoundException("Mail project link not found");
    return matches.getFirst();
  }

  private Link map(java.sql.ResultSet rs) throws java.sql.SQLException {
    Timestamp last = rs.getTimestamp("last_synced_at");
    return new Link(Long.toString(rs.getLong("mail_project_id")),
        Long.toString(rs.getLong("company_id")), rs.getString("mode"),
        rs.getString("state"), rs.getTimestamp("effective_from").toInstant(),
        Long.toString(rs.getLong("last_external_usage_id")),
        last == null ? null : last.toInstant(), rs.getString("ownership_proof"));
  }
}
