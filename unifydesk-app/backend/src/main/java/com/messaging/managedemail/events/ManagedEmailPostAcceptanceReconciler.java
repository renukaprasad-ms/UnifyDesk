package com.messaging.managedemail.events;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailPostAcceptanceReconciler {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailSuppressionFeedbackService suppressionService;

  public void reconcile(Long recipientId) {
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime =
        runtimeRepository.findRecipientRuntime(recipientId).orElse(null);
    if (runtime == null || !"ACCEPTED_BY_PROVIDER".equals(runtime.recipientStatus())) return;

    List<Timestamp> hardBounceTimes =
        jdbcTemplate.queryForList(
            """
            select event_timestamp
              from managed_email_provider_events
             where recipient_id = ?
               and company_id = ?
               and event_type = 'BOUNCE'
               and upper(coalesce(bounce_type, '')) = 'PERMANENT'
               and correlation_status = 'MATCHED'
             order by event_timestamp asc
             limit 1
            """,
            Timestamp.class,
            recipientId,
            runtime.companyId());
    if (hardBounceTimes.isEmpty()) return;

    Instant eventAt = hardBounceTimes.getFirst().toInstant();
    suppressionService.hardBounce(runtime, eventAt);
  }
}
