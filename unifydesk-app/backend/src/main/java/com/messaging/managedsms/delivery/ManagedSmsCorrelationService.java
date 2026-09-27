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
    List<String> existing =
        jdbcTemplate.queryForList(
            "select correlation_token from managed_sms_correlations where recipient_id = ? and attempt_no = ?",
            String.class,
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
  public void accepted(String token, String providerRequestId) {
    String requestId = trim(providerRequestId);
    jdbcTemplate.update(
        """
        update managed_sms_correlations
           set provider_request_id = coalesce(provider_request_id, ?)
         where correlation_token = ?
           and (provider_request_id is null or provider_request_id = ?)
        """,
        requestId,
        token,
        requestId);
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
