package com.messaging.managedemail.events;

import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.service.BroadcastStatsService;
import com.messaging.channel.enums.ChannelType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.integration.TrustedProviderSuppressionBridge;
import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailSuppressionFeedbackService {

  private final JdbcTemplate jdbcTemplate;
  private final BroadcastStatsService statsService;
  private final ManagedEmailIdGenerator idGenerator;
  private final TrustedProviderSuppressionBridge contactSuppressionBridge;

  @Transactional
  public void hardBounce(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      ManagedEmailProviderEvent event) {
    upsertSuppression(runtime, "HARD_BOUNCE");
    convergeContactSuppression(runtime, event, SuppressionReason.HARD_BOUNCE);
    transitionBounce(runtime, event.eventTimestamp());
  }

  @Transactional
  public void complaint(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      ManagedEmailProviderEvent event) {
    upsertSuppression(runtime, "COMPLAINT");
    convergeContactSuppression(runtime, event, SuppressionReason.COMPLAINT);
  }

  private void convergeContactSuppression(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      ManagedEmailProviderEvent event,
      SuppressionReason reason) {
    contactSuppressionBridge.suppressRecipient(
        runtime.companyId(),
        runtime.recipientId(),
        IdentityType.EMAIL,
        Channel.EMAIL,
        reason,
        PreferenceSource.MANAGED_EMAIL_EVENT,
        "sns:" + event.snsMessageId(),
        event.eventTimestamp(),
        "managed-email:"
            + event.snsMessageId()
            + ":"
            + event.type().name()
            + ":"
            + runtime.recipientId());
  }

  private void upsertSuppression(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime, String reason) {
    jdbcTemplate.update(
        """
        insert into broadcast_suppressions
          (id, company_id, channel_type, normalized_destination, reason,
           source_recipient_id, active, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, true, now(), now())
        on conflict (company_id, channel_type, normalized_destination) do update
          set active = true,
              reason = case
                when broadcast_suppressions.reason = 'COMPLAINT' then 'COMPLAINT'
                else excluded.reason
              end,
              source_recipient_id = excluded.source_recipient_id,
              updated_at = now()
        """,
        idGenerator.nextId(),
        runtime.companyId(),
        ChannelType.EMAIL.name(),
        runtime.normalizedDestination(),
        reason,
        runtime.recipientId());
  }

  private void transitionBounce(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime, Instant eventAt) {
    List<String> current =
        jdbcTemplate.queryForList(
            """
            select status
              from broadcast_recipients
             where id = ?
               and company_id = ?
             for update
            """,
            String.class,
            runtime.recipientId(),
            runtime.companyId());
    if (current.isEmpty()) return;
    BroadcastRecipientStatus from = BroadcastRecipientStatus.valueOf(current.getFirst());
    if (from != BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER
        && from != BroadcastRecipientStatus.UNKNOWN) {
      return;
    }
    int changed =
        jdbcTemplate.update(
            """
            update broadcast_recipients
               set status = 'BOUNCED',
                   bounced_at = coalesce(bounced_at, ?),
                   updated_at = now()
             where id = ?
               and company_id = ?
               and status = ?
            """,
            Timestamp.from(eventAt),
            runtime.recipientId(),
            runtime.companyId(),
            from.name());
    if (changed == 1) {
      statsService.transition(runtime.runId(), from, BroadcastRecipientStatus.BOUNCED);
    }
  }
}
