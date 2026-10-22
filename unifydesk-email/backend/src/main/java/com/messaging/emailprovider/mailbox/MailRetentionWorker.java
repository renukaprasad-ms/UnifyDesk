package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.mailbox.inbound.MailSpoolStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in, bounded, two-phase retention. Backup attestation and legal holds are operator-owned. */
@Component
@ConditionalOnProperty(prefix = "app.mail.retention", name = "enabled", havingValue = "true")
public class MailRetentionWorker {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final MailSpoolStore spool;

  private record Candidate(long id, long org, long mailbox, String key, long size) {}

  public MailRetentionWorker(JdbcTemplate jdbc, TransactionTemplate tx, MailSpoolStore spool) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.spool = spool;
  }

  @Scheduled(fixedDelayString = "${app.mail.retention.interval-ms:60000}")
  public void poll() {
    // Phase 1 must commit before phase 2 can unlink. Both are replay-safe.
    for (int i = 0; i < 10; i++) if (!markOne()) break;
    for (int i = 0; i < 10; i++) if (!purgeOne()) break;
  }

  public boolean markOne() {
    return Boolean.TRUE.equals(
        tx.execute(
            ignored -> {
              var candidates =
                  jdbc.query(
                      """
          SELECT msg.id,msg.organization_id,msg.mailbox_id,msg.mime_object_key,msg.mime_size_bytes
          FROM email_mailbox_messages msg JOIN email_mailboxes m
            ON m.id=msg.mailbox_id AND m.organization_id=msg.organization_id
          WHERE msg.folder='TRASH' AND msg.deleted_at IS NOT NULL AND msg.purged_at IS NULL
            AND m.legal_hold=FALSE AND m.trash_retention_days BETWEEN 7 AND 3650
            AND msg.trashed_at < now() - m.trash_retention_days * INTERVAL '1 day'
            AND m.backup_verified_at>=msg.trashed_at AND m.backup_reference IS NOT NULL
            AND NOT EXISTS(SELECT 1 FROM email_mail_object_gc gc WHERE gc.message_id=msg.id)
            AND NOT EXISTS(SELECT 1 FROM email_mail_outbound_recipients r
              WHERE r.intent_id=msg.outbound_intent_id AND r.state NOT IN ('HANDED_OFF','FAILED'))
            AND NOT EXISTS(SELECT 1 FROM email_mailbox_messages other
              WHERE other.mime_object_key=msg.mime_object_key AND other.id<>msg.id)
            AND (EXISTS(SELECT 1 FROM email_mail_inbound_jobs j WHERE j.id=msg.inbound_job_id
                 AND j.mime_object_key=msg.mime_object_key AND j.mime_size_bytes=msg.mime_size_bytes AND j.status='READY')
              OR EXISTS(SELECT 1 FROM email_mail_outbound_archives a WHERE a.intent_id=msg.outbound_intent_id
                 AND a.mime_object_key=msg.mime_object_key AND a.mime_size_bytes=msg.mime_size_bytes))
          ORDER BY msg.trashed_at,msg.id FOR UPDATE OF m,msg SKIP LOCKED LIMIT 1
          """,
                      (rs, row) ->
                          new Candidate(
                              rs.getLong(1),
                              rs.getLong(2),
                              rs.getLong(3),
                              rs.getString(4),
                              rs.getLong(5)));
              if (candidates.isEmpty()) return false;
              Candidate candidate = candidates.getFirst();
              jdbc.update(
                  "INSERT INTO email_mail_object_gc(object_key,organization_id,mailbox_id,message_id,size_bytes) VALUES(?,?,?,?,?)",
                  candidate.key(),
                  candidate.org(),
                  candidate.mailbox(),
                  candidate.id(),
                  candidate.size());
              audit(candidate, "PURGE_MARKED");
              return true;
            }));
  }

  public boolean purgeOne() {
    return Boolean.TRUE.equals(
        tx.execute(
            ignored -> {
              var candidates =
                  jdbc.query(
                      """
          SELECT gc.message_id,gc.organization_id,gc.mailbox_id,gc.object_key,gc.size_bytes
          FROM email_mail_object_gc gc JOIN email_mailboxes m
            ON m.id=gc.mailbox_id AND m.organization_id=gc.organization_id
          JOIN email_mailbox_messages msg ON msg.id=gc.message_id
          WHERE gc.state='PURGING' AND m.legal_hold=FALSE AND m.trash_retention_days>=7
            AND msg.trashed_at < now() - m.trash_retention_days * INTERVAL '1 day'
            AND m.backup_verified_at>=msg.trashed_at AND m.backup_reference IS NOT NULL
          ORDER BY gc.marked_at FOR UPDATE OF m,gc,msg SKIP LOCKED LIMIT 1
          """,
                      (rs, row) ->
                          new Candidate(
                              rs.getLong(1),
                              rs.getLong(2),
                              rs.getLong(3),
                              rs.getString(4),
                              rs.getLong(5)));
              if (candidates.isEmpty()) return false;
              Candidate candidate = candidates.getFirst();
              Long used =
                  jdbc.queryForObject(
                      "SELECT storage_used_bytes FROM email_mailboxes WHERE id=?",
                      Long.class,
                      candidate.mailbox());
              if (used == null || used < candidate.size())
                throw new IllegalStateException(
                    "Storage accounting must be reconciled before purge");
              spool.purge(new MailSpoolStore.Stored(candidate.key(), candidate.size(), ""));
              jdbc.update(
                  "UPDATE email_mailboxes SET storage_used_bytes=storage_used_bytes-?,updated_at=now() WHERE id=? AND organization_id=?",
                  candidate.size(),
                  candidate.mailbox(),
                  candidate.org());
              jdbc.update(
                  "UPDATE email_mailbox_messages SET purged_at=now(),updated_at=now() WHERE id=?",
                  candidate.id());
              jdbc.update(
                  "UPDATE email_mail_object_gc SET state='PURGED',completed_at=now() WHERE object_key=?",
                  candidate.key());
              audit(candidate, "PURGE_COMPLETED");
              return true;
            }));
  }

  private void audit(Candidate candidate, String action) {
    jdbc.update(
        "INSERT INTO email_mail_retention_audit(id,organization_id,mailbox_id,message_id,action) VALUES(?,?,?,?,?)",
        IdGenerator.nextId(),
        candidate.org(),
        candidate.mailbox(),
        candidate.id(),
        action);
  }
}
