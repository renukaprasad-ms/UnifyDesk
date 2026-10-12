package com.messaging.emailprovider.queue;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

  @Query(
      value =
          """
          SELECT * FROM email_outbox
          WHERE status = 'PENDING'
            AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
          ORDER BY created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<EmailOutbox> findClaimable(@Param("now") Instant now, @Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          UPDATE email_outbox
          SET status = 'PENDING', claimed_at = NULL, updated_at = :now
          WHERE status = 'PROCESSING' AND claimed_at < :staleBefore
          """,
      nativeQuery = true)
  int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
