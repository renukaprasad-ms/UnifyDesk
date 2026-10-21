package com.messaging.emailprovider.mailbox.compose;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.product.console.ConsoleAuthService;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mailbox-only draft state. Public marketing API keys cannot use this service. R6 outbound intents
 * remain HELD, and are never dispatched automatically.
 */
@Service
@ConditionalOnProperty(prefix = "app.mail.compose", name = "enabled", havingValue = "true")
public class MailComposeService {
  private static final int MAX_DRAFTS = 50;
  private static final int MAX_RECIPIENTS = 20;
  private static final int MAX_BODY_BYTES = 65536;
  private static final Pattern ADDRESS =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._%+-]{0,63}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");

  private final JdbcTemplate jdbc;
  private final boolean submissionEnabled;
  private final boolean outboundEnabled;

  @Autowired
  public MailComposeService(
      JdbcTemplate jdbc,
      @Value("${app.mail.compose.submission-enabled:false}") boolean submissionEnabled,
      @Value("${app.mail.outbound.enabled:false}") boolean outboundEnabled) {
    this.jdbc = jdbc;
    this.submissionEnabled = submissionEnabled;
    this.outboundEnabled = outboundEnabled;
  }

  /** Small constructor retained for isolated validation tests. */
  public MailComposeService(JdbcTemplate jdbc, boolean submissionEnabled) {
    this(jdbc, submissionEnabled, false);
  }

  public record DraftInput(
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String subject,
      String bodyText,
      String replyToMessageId) {}

  public record Draft(
      String id,
      String mailboxId,
      String authorUserId,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String subject,
      String bodyText,
      String replyToMessageId,
      String state,
      long revision,
      String updatedAt) {}

  public record DraftSummary(
      String id, String subject, String state, long revision, String updatedAt) {}

  public record HeldSubmission(String draftId, String intentId, String state) {}

  private record Recipient(String address, String type) {}

  private record Normalized(
      String to, String cc, String bcc, String subject, String body, Long replyId) {}

  @Transactional
  public Draft create(ConsoleAuthService.Principal user, long mailboxId, DraftInput input) {
    requireMailbox(user, mailboxId, true);
    Long count =
        jdbc.queryForObject(
            """
        SELECT COUNT(*) FROM email_mail_drafts
        WHERE organization_id=? AND mailbox_id=? AND author_user_id=?
        """,
            Long.class,
            user.organizationId(),
            mailboxId,
            user.userId());
    if (count == null || count >= MAX_DRAFTS) {
      throw new ConflictException("Maximum saved draft count reached");
    }
    Normalized safe = normalize(input, user, mailboxId);
    long id = IdGenerator.nextId();
    jdbc.update(
        """
        INSERT INTO email_mail_drafts(id,organization_id,mailbox_id,author_user_id,
          reply_to_message_id,recipients_to,recipients_cc,recipients_bcc,
          subject,body_text,status,revision)
        VALUES (?,?,?,?,?,?,?,?,?,?,'DRAFT',1)
        """,
        id,
        user.organizationId(),
        mailboxId,
        user.userId(),
        safe.replyId(),
        safe.to(),
        safe.cc(),
        safe.bcc(),
        safe.subject(),
        safe.body());
    return get(user, mailboxId, id);
  }

  @Transactional
  public Draft update(
      ConsoleAuthService.Principal user,
      long mailboxId,
      long draftId,
      long expectedRevision,
      DraftInput input) {
    requireMailbox(user, mailboxId, true);
    if (expectedRevision < 1) throw new BadRequestException("Invalid draft revision");
    List<Long> revision =
        jdbc.query(
            """
        SELECT revision FROM email_mail_drafts
        WHERE id=? AND organization_id=? AND mailbox_id=? AND author_user_id=?
          AND status='DRAFT' FOR UPDATE
        """,
            (rs, row) -> rs.getLong(1),
            draftId,
            user.organizationId(),
            mailboxId,
            user.userId());
    if (revision.isEmpty()) throw new NotFoundException("Editable draft not found");
    if (revision.get(0) != expectedRevision) {
      throw new ConflictException("Draft changed; refresh before saving");
    }
    Normalized safe = normalize(input, user, mailboxId);
    int changed =
        jdbc.update(
            """
        UPDATE email_mail_drafts
        SET reply_to_message_id=?,recipients_to=?,recipients_cc=?,recipients_bcc=?,
            subject=?,body_text=?,revision=revision+1,updated_at=now()
        WHERE id=? AND organization_id=? AND mailbox_id=? AND author_user_id=?
          AND status='DRAFT' AND revision=?
        """,
            safe.replyId(),
            safe.to(),
            safe.cc(),
            safe.bcc(),
            safe.subject(),
            safe.body(),
            draftId,
            user.organizationId(),
            mailboxId,
            user.userId(),
            expectedRevision);
    if (changed != 1) throw new ConflictException("Draft changed; refresh before saving");
    return get(user, mailboxId, draftId);
  }

  @Transactional(readOnly = true)
  public List<DraftSummary> list(ConsoleAuthService.Principal user, long mailboxId) {
    requireMailbox(user, mailboxId, false);
    return jdbc.query(
        """
        SELECT id,subject,status,revision,updated_at
        FROM email_mail_drafts
        WHERE organization_id=? AND mailbox_id=? AND author_user_id=?
        ORDER BY id DESC LIMIT 50
        """,
        (rs, row) ->
            new DraftSummary(
                Long.toString(rs.getLong(1)),
                rs.getString(2),
                rs.getString(3),
                rs.getLong(4),
                rs.getTimestamp(5).toInstant().toString()),
        user.organizationId(),
        mailboxId,
        user.userId());
  }

  @Transactional(readOnly = true)
  public Draft get(ConsoleAuthService.Principal user, long mailboxId, long draftId) {
    requireMailbox(user, mailboxId, false);
    List<Draft> matches =
        jdbc.query(
            """
        SELECT id,mailbox_id,author_user_id,recipients_to,recipients_cc,
          recipients_bcc,subject,body_text,reply_to_message_id,
          status,revision,updated_at
        FROM email_mail_drafts
        WHERE id=? AND organization_id=? AND mailbox_id=? AND author_user_id=?
        """,
            this::mapDraft,
            draftId,
            user.organizationId(),
            mailboxId,
            user.userId());
    if (matches.isEmpty()) throw new NotFoundException("Draft not found");
    return matches.get(0);
  }

  @Transactional
  public void delete(ConsoleAuthService.Principal user, long mailboxId, long draftId) {
    requireMailbox(user, mailboxId, true);
    int deleted =
        jdbc.update(
            """
        DELETE FROM email_mail_drafts
        WHERE id=? AND organization_id=? AND mailbox_id=?
          AND author_user_id=? AND status='DRAFT'
        """,
            draftId,
            user.organizationId(),
            mailboxId,
            user.userId());
    if (deleted != 1) throw new NotFoundException("Editable draft not found");
  }

  /**
   * Produces a durable HELD submission only. No SMTP handoff or 'Sent' confirmation is permitted
   * until the next transport/security increment.
   */
  @Transactional
  public HeldSubmission hold(
      ConsoleAuthService.Principal user, long mailboxId, long draftId, String idempotencyKey) {
    if (!submissionEnabled || !outboundEnabled) {
      throw new ServiceUnavailableException("Mailbox sending transport is not available");
    }
    requireMailbox(user, mailboxId, true);
    Boolean ready =
        jdbc.queryForObject(
            """
        SELECT EXISTS(SELECT 1 FROM email_mailboxes m
          JOIN email_mail_domains d ON d.id=m.domain_id
            AND d.organization_id=m.organization_id
          JOIN email_product_organizations org ON org.id=m.organization_id
          JOIN email_product_projects p ON p.organization_id=org.id
            AND p.status='ACTIVE' AND p.sending_enabled=true
          JOIN sending_domains sd ON sd.company_id=p.id
            AND sd.domain_name=d.domain_name AND sd.status='VERIFIED'
            AND sd.dkim_verified_at IS NOT NULL
          JOIN dkim_keys k ON k.sending_domain_id=sd.id
            AND k.active=true AND k.verified_at IS NOT NULL
          WHERE m.id=? AND m.organization_id=? AND m.status='ACTIVE'
            AND d.status='VERIFIED' AND d.outbound_enabled=true
            AND org.status='ACTIVE')
        """,
            Boolean.class,
            mailboxId,
            user.organizationId());
    if (!Boolean.TRUE.equals(ready)) {
      throw new ServiceUnavailableException("Mailbox outbound domain is not approved");
    }
    if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
      throw new BadRequestException("Idempotency key must contain 8-128 safe characters");
    }
    List<Draft> drafts =
        jdbc.query(
            """
        SELECT id,mailbox_id,author_user_id,recipients_to,recipients_cc,
          recipients_bcc,subject,body_text,reply_to_message_id,
          status,revision,updated_at
        FROM email_mail_drafts
        WHERE id=? AND organization_id=? AND mailbox_id=? AND author_user_id=?
        FOR UPDATE
        """,
            this::mapDraft,
            draftId,
            user.organizationId(),
            mailboxId,
            user.userId());
    if (drafts.isEmpty()) throw new NotFoundException("Draft not found");
    Draft draft = drafts.get(0);
    if ("PENDING_TRANSPORT".equals(draft.state())) {
      String previously =
          jdbc.queryForObject(
              """
          SELECT submission_key FROM email_mail_drafts WHERE id=? AND organization_id=?
          """,
              String.class,
              draftId,
              user.organizationId());
      if (!idempotencyKey.equals(previously)) {
        throw new ConflictException("Draft already held under a different submission key");
      }
      return existingIntent(user, mailboxId, draftId);
    }
    if (draft.to().isEmpty() && draft.cc().isEmpty() && draft.bcc().isEmpty()) {
      throw new BadRequestException("At least one recipient is required");
    }
    if (draft.subject().isBlank() || draft.bodyText().isBlank()) {
      throw new BadRequestException("Subject and plain-text body are required");
    }
    long intentId = IdGenerator.nextId();
    try {
      jdbc.update(
          """
          UPDATE email_mail_drafts SET status='PENDING_TRANSPORT',submission_key=?,
            submitted_at=now(),updated_at=now()
          WHERE id=? AND organization_id=? AND mailbox_id=? AND author_user_id=?
            AND status='DRAFT'
          """,
          idempotencyKey,
          draftId,
          user.organizationId(),
          mailboxId,
          user.userId());
      jdbc.update(
          """
          INSERT INTO email_mail_outbound_intents
            (id,organization_id,mailbox_id,draft_id,state)
          VALUES (?,?,?,?,'HELD')
          """,
          intentId,
          user.organizationId(),
          mailboxId,
          draftId);
      for (Recipient recipient : outboundRecipients(draft)) {
        jdbc.update(
            """
            INSERT INTO email_mail_outbound_recipients
              (id,organization_id,mailbox_id,intent_id,recipient_address,recipient_type)
            VALUES (?,?,?,?,?,?)
            """,
            IdGenerator.nextId(),
            user.organizationId(),
            mailboxId,
            intentId,
            recipient.address(),
            recipient.type());
      }
    } catch (DuplicateKeyException collision) {
      // Prevent a key being applied to two separate drafts in one mailbox.
      throw new ConflictException("Submission key already used");
    }
    return new HeldSubmission(Long.toString(draftId), Long.toString(intentId), "HELD");
  }

  private HeldSubmission existingIntent(
      ConsoleAuthService.Principal user, long mailboxId, long draftId) {
    List<String> found =
        jdbc.query(
            """
        SELECT id FROM email_mail_outbound_intents
        WHERE draft_id=? AND organization_id=? AND mailbox_id=?
        """,
            (rs, row) -> Long.toString(rs.getLong(1)),
            draftId,
            user.organizationId(),
            mailboxId);
    if (found.isEmpty()) throw new ServiceUnavailableException("Outgoing intent unavailable");
    String state =
        jdbc.queryForObject(
            """
        SELECT state FROM email_mail_outbound_intents WHERE id=? AND organization_id=?
        """,
            String.class,
            Long.parseLong(found.get(0)),
            user.organizationId());
    return new HeldSubmission(Long.toString(draftId), found.get(0), state);
  }

  private static List<Recipient> outboundRecipients(Draft draft) {
    java.util.ArrayList<Recipient> results = new java.util.ArrayList<>();
    for (String address : draft.to()) results.add(new Recipient(address, "TO"));
    for (String address : draft.cc()) results.add(new Recipient(address, "CC"));
    for (String address : draft.bcc()) results.add(new Recipient(address, "BCC"));
    return results;
  }

  private Draft mapDraft(ResultSet rs, int row) throws SQLException {
    long replyId = rs.getLong(9);
    String reply = rs.wasNull() ? null : Long.toString(replyId);
    return new Draft(
        Long.toString(rs.getLong(1)),
        Long.toString(rs.getLong(2)),
        Long.toString(rs.getLong(3)),
        split(rs.getString(4)),
        split(rs.getString(5)),
        split(rs.getString(6)),
        rs.getString(7),
        rs.getString(8),
        reply,
        rs.getString(10),
        rs.getLong(11),
        rs.getTimestamp(12).toInstant().toString());
  }

  private static List<String> split(String value) {
    return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
  }

  private Normalized normalize(
      DraftInput input, ConsoleAuthService.Principal user, long mailboxId) {
    if (input == null) throw new BadRequestException("Draft is required");
    Set<String> unique = new HashSet<>();
    String to = canonical(input.to(), unique);
    String cc = canonical(input.cc(), unique);
    String bcc = canonical(input.bcc(), unique);
    if (unique.size() > MAX_RECIPIENTS) throw new BadRequestException("Maximum 20 recipients");
    String subject = input.subject() == null ? "" : input.subject().trim();
    if (subject.length() > 998 || subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
      throw new BadRequestException("Invalid email subject");
    }
    String body = input.bodyText() == null ? "" : input.bodyText();
    if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
      throw new BadRequestException("Draft body exceeds 64 KiB");
    }
    Long replyId = null;
    if (input.replyToMessageId() != null && !input.replyToMessageId().isBlank()) {
      try {
        if (!input.replyToMessageId().matches("[1-9][0-9]{0,18}")) {
          throw new NumberFormatException();
        }
        replyId = Long.parseLong(input.replyToMessageId());
      } catch (NumberFormatException invalid) {
        throw new BadRequestException("Invalid reply message ID");
      }
      Boolean valid =
          jdbc.queryForObject(
              """
          SELECT EXISTS(SELECT 1 FROM email_mailbox_messages m
            JOIN email_mail_inbound_jobs j ON j.id=m.inbound_job_id
              AND j.organization_id=m.organization_id AND j.mailbox_id=m.mailbox_id
            WHERE m.id=? AND m.organization_id=? AND m.mailbox_id=?
              AND m.direction='INBOUND' AND m.deleted_at IS NULL
              AND j.status='READY')
          """,
              Boolean.class,
              replyId,
              user.organizationId(),
              mailboxId);
      if (!Boolean.TRUE.equals(valid)) throw new NotFoundException("Reply target not found");
    }
    return new Normalized(to, cc, bcc, subject, body, replyId);
  }

  static String canonical(List<String> addresses, Set<String> unique) {
    if (addresses == null) return "";
    if (addresses.size() > MAX_RECIPIENTS) throw new BadRequestException("Too many recipients");
    java.util.ArrayList<String> output = new java.util.ArrayList<>();
    for (String address : addresses) {
      String normalized = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
      if (normalized.length() > 320
          || !ADDRESS.matcher(normalized).matches()
          || normalized.contains("..")
          || normalized.contains(".@")) {
        throw new BadRequestException("Invalid recipient email address");
      }
      if (!unique.add(normalized)) throw new BadRequestException("Duplicate recipient");
      output.add(normalized);
    }
    return String.join(",", output);
  }

  /** A short row lock serializes quota claims and verifies membership. */
  private void requireMailbox(ConsoleAuthService.Principal user, long mailboxId, boolean lock) {
    String query =
        """
        SELECT m.id FROM email_mailboxes m
        JOIN email_mail_domains d ON d.id=m.domain_id AND d.organization_id=m.organization_id
        JOIN email_product_organizations o ON o.id=m.organization_id
        JOIN email_mailbox_access a ON a.mailbox_id=m.id AND a.organization_id=m.organization_id
        WHERE m.id=? AND m.organization_id=? AND a.user_id=?
          AND m.status='ACTIVE' AND d.status='VERIFIED' AND o.status='ACTIVE'
        """
            + (lock ? " FOR UPDATE OF m" : "");
    List<Long> found =
        jdbc.query(
            query, (rs, row) -> rs.getLong(1), mailboxId, user.organizationId(), user.userId());
    if (found.isEmpty()) throw new NotFoundException("Mailbox not found");
  }
}
