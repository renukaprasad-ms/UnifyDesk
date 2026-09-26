package com.messaging.managedemail.events;

import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

@Repository
@RequiredArgsConstructor
public class JdbcManagedEmailProviderEffectRepository
    implements ManagedEmailProviderEffectRepository {

  static final String EFFECT_TYPE_TENANT_SUPPRESSION = "TENANT_SUPPRESSION";

  private final JdbcTemplate jdbcTemplate;
  private final ManagedEmailIdGenerator idGenerator;

  @Override
  @Transactional
  public void ensureTenantSuppressionScheduled(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      SuppressionListReason reason) {
    if (!available()) {
      throw new IllegalStateException("Managed Email provider effect storage is unavailable");
    }

    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_provider_effects
              (id, event_id, company_id, tenant_id, recipient_id, effect_type, effect_reason,
               status, attempt_count, next_attempt_at, created_at, updated_at)
            select ?, provider_event.id, ?, ?, ?, ?, ?, 'PENDING', 0, now(), now(), now()
              from managed_email_provider_events provider_event
             where provider_event.sns_message_id = ?
                or provider_event.normalized_event_key = ?
             order by provider_event.received_at asc
             limit 1
            on conflict (event_id, effect_type) do nothing
            """,
            idGenerator.nextId(),
            runtime.companyId(),
            runtime.tenantId(),
            runtime.recipientId(),
            EFFECT_TYPE_TENANT_SUPPRESSION,
            reason.toString(),
            event.snsMessageId(),
            ManagedEmailProviderEventKeys.normalized(event));

    if (inserted == 1) {
      return;
    }

    List<String> reasons =
        jdbcTemplate.queryForList(
            """
            select effect.effect_reason
              from managed_email_provider_effects effect
              join managed_email_provider_events provider_event
                on provider_event.id = effect.event_id
             where (provider_event.sns_message_id = ?
                 or provider_event.normalized_event_key = ?)
               and effect.effect_type = ?
             order by effect.id asc
             limit 1
            """,
            String.class,
            event.snsMessageId(),
            ManagedEmailProviderEventKeys.normalized(event),
            EFFECT_TYPE_TENANT_SUPPRESSION);
    if (reasons.isEmpty()) {
      throw new IllegalStateException("Managed Email provider event receipt is missing");
    }
    if (!reason.toString().equals(reasons.getFirst())) {
      throw new IllegalStateException("Managed Email provider effect reason mismatch");
    }
  }

  @Override
  public boolean available() {
    Boolean available =
        jdbcTemplate.queryForObject(
            "select to_regclass('managed_email_provider_effects') is not null", Boolean.class);
    return Boolean.TRUE.equals(available);
  }

  @Override
  @Transactional
  public Optional<ClaimedProviderEffect> claimNext(Instant now, Instant staleBefore) {
    if (!available()) {
      return Optional.empty();
    }

    jdbcTemplate.update(
        """
        update managed_email_provider_effects
           set status = 'RETRY',
               claimed_at = null,
               next_attempt_at = ?,
               last_error_code = 'STALE_CLAIM_RECOVERED',
               last_error_message = 'Recovered stale provider-effect claim',
               updated_at = ?
         where status = 'PROCESSING'
           and claimed_at < ?
        """,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(staleBefore));

    List<ClaimedProviderEffect> effects =
        jdbcTemplate.query(
            """
            select id, event_id, company_id, tenant_id, recipient_id, effect_reason, attempt_count
              from managed_email_provider_effects
             where status in ('PENDING', 'RETRY')
               and next_attempt_at <= ?
             order by id asc
             for update skip locked
             limit 1
            """,
            (resultSet, rowNum) ->
                new ClaimedProviderEffect(
                    resultSet.getLong("id"),
                    resultSet.getLong("event_id"),
                    resultSet.getLong("company_id"),
                    resultSet.getLong("tenant_id"),
                    resultSet.getLong("recipient_id"),
                    SuppressionListReason.fromValue(resultSet.getString("effect_reason")),
                    resultSet.getInt("attempt_count") + 1),
            Timestamp.from(now));
    if (effects.isEmpty()) {
      return Optional.empty();
    }

    ClaimedProviderEffect effect = effects.getFirst();
    int updated =
        jdbcTemplate.update(
            """
            update managed_email_provider_effects
               set status = 'PROCESSING',
                   attempt_count = ?,
                   claimed_at = ?,
                   last_error_code = null,
                   last_error_message = null,
                   updated_at = ?
             where id = ?
               and status in ('PENDING', 'RETRY')
            """,
            effect.attemptCount(),
            Timestamp.from(now),
            Timestamp.from(now),
            effect.id());
    if (updated != 1) {
      throw new IllegalStateException("Managed Email provider effect claim was lost");
    }
    return Optional.of(effect);
  }

  @Override
  @Transactional
  public void succeeded(Long effectId, int attemptCount, Instant completedAt) {
    int updated =
        jdbcTemplate.update(
            """
            update managed_email_provider_effects
               set status = 'SUCCEEDED',
                   claimed_at = null,
                   completed_at = ?,
                   last_error_code = null,
                   last_error_message = null,
                   updated_at = ?
             where id = ?
               and status = 'PROCESSING'
               and attempt_count = ?
            """,
            Timestamp.from(completedAt),
            Timestamp.from(completedAt),
            effectId,
            attemptCount);
    if (updated != 1) {
      throw new IllegalStateException("Managed Email provider effect completion was not persisted");
    }
  }

  @Override
  @Transactional
  public void retry(
      Long effectId, int attemptCount, Instant nextAttemptAt, String errorCode) {
    int updated =
        jdbcTemplate.update(
            """
            update managed_email_provider_effects
               set status = 'RETRY',
                   claimed_at = null,
                   next_attempt_at = ?,
                   last_error_code = ?,
                   last_error_message = 'Managed Email provider suppression failed',
                   updated_at = now()
             where id = ?
               and status = 'PROCESSING'
               and attempt_count = ?
            """,
            Timestamp.from(nextAttemptAt),
            safeCode(errorCode),
            effectId,
            attemptCount);
    if (updated != 1) {
      throw new IllegalStateException("Managed Email provider effect retry was not persisted");
    }
  }

  @Override
  @Transactional
  public void failed(
      Long effectId, int attemptCount, Instant completedAt, String errorCode) {
    int updated =
        jdbcTemplate.update(
            """
            update managed_email_provider_effects
               set status = 'FAILED',
                   claimed_at = null,
                   completed_at = ?,
                   last_error_code = ?,
                   last_error_message = 'Managed Email provider suppression requires operator action',
                   updated_at = ?
             where id = ?
               and status = 'PROCESSING'
               and attempt_count = ?
            """,
            Timestamp.from(completedAt),
            safeCode(errorCode),
            Timestamp.from(completedAt),
            effectId,
            attemptCount);
    if (updated != 1) {
      throw new IllegalStateException("Managed Email provider effect failure was not persisted");
    }
  }

  private String safeCode(String errorCode) {
    if (errorCode == null || errorCode.isBlank()) {
      return "PROVIDER_EFFECT_FAILED";
    }
    String value = errorCode.replaceAll("[^A-Za-z0-9_.-]", "_");
    return value.substring(0, Math.min(128, value.length()));
  }
}
