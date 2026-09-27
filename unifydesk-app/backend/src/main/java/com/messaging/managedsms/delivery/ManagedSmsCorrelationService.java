package com.messaging.managedsms.delivery;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsCorrelationService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public String create(Long companyId, Long runId, Long recipientId, int attemptNo) {
    requireRecipientTuple(companyId, runId, recipientId);

    List<String> existing =
        jdbcTemplate.queryForList(
            """
            select correlation_token
              from managed_sms_correlations
             where company_id = ?
               and run_id = ?
               and recipient_id = ?
               and attempt_no = ?
            """,
            String.class,
            companyId,
            runId,
            recipientId,
            attemptNo);
    if (!existing.isEmpty()) return existing.getFirst();

    for (int i = 0; i < 5; i++) {
      byte[] bytes = new byte[16];
      RANDOM.nextBytes(bytes);
      String token = HexFormat.of().formatHex(bytes);
      int inserted =
          jdbcTemplate.update(
              """
              insert into managed_sms_correlations (
                correlation_token, company_id, run_id, recipient_id, attempt_no,
                provider_request_id, created_at, expires_at
              ) values (?, ?, ?, ?, ?, null, ?, ?)
              on conflict do nothing
              """,
              token,
              companyId,
              runId,
              recipientId,
              attemptNo,
              Timestamp.from(Instant.now()),
              Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
      if (inserted == 1) return token;
    }
    throw new IllegalStateException("Unable to allocate managed SMS correlation token");
  }

  @Transactional
  public void accepted(
      Long companyId,
      Long runId,
      Long recipientId,
      int attemptNo,
      String token,
      String providerRequestId) {
    requireRecipientTuple(companyId, runId, recipientId);
    String requestId = trim(providerRequestId);
    int updated =
        jdbcTemplate.update(
            """
            update managed_sms_correlations
               set provider_request_id = coalesce(provider_request_id, ?)
             where correlation_token = ?
               and company_id = ?
               and run_id = ?
               and recipient_id = ?
               and attempt_no = ?
               and (provider_request_id is null or provider_request_id = ?)
            """,
            requestId,
            token,
            companyId,
            runId,
            recipientId,
            attemptNo,
            requestId);
    if (updated != 1) {
      throw new IllegalStateException("Managed SMS correlation ownership mismatch");
    }
  }

  private void requireRecipientTuple(Long companyId, Long runId, Long recipientId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from broadcast_recipients r
              join managed_sms_run_bindings rb
                on rb.run_id = r.run_id
               and rb.company_id = r.company_id
             where r.company_id = ?
               and r.run_id = ?
               and r.id = ?
            """,
            Integer.class,
            companyId,
            runId,
            recipientId);
    if (count == null || count != 1) {
      throw new IllegalStateException("Managed SMS recipient ownership mismatch");
    }
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
