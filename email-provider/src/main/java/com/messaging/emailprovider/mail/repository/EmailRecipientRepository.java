package com.messaging.emailprovider.mail.repository;

import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailRecipientRepository extends JpaRepository<EmailRecipient, Long> {

  List<EmailRecipient> findAllByMessageIdOrderByCreatedAtAsc(Long messageId);

  List<EmailRecipient> findAllByStatusOrderByCreatedAtAsc(EmailRecipientStatus status);

  Optional<EmailRecipient> findFirstByPostfixQueueId(String postfixQueueId);

  @Query(
      value =
          """
          SELECT id FROM email_recipients
          WHERE message_id = :messageId
            AND (
              status = 'QUEUED'
              OR (
                status = 'DEFERRED'
                AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              )
            )
          ORDER BY created_at
          """,
      nativeQuery = true)
  List<Long> findDueRecipientIds(@Param("messageId") Long messageId, @Param("now") Instant now);

  @Query(
      value =
          """
          SELECT DISTINCT message_id FROM email_recipients
          WHERE status = 'DEFERRED'
            AND next_attempt_at <= :now
          """,
      nativeQuery = true)
  List<Long> findMessageIdsWithDueRetries(@Param("now") Instant now);

  @Modifying
  @Query(
      value =
          """
          UPDATE email_recipients
          SET status = 'PROCESSING',
              attempt_count = attempt_count + 1,
              last_attempt_at = :now,
              next_attempt_at = NULL,
              updated_at = :now
          WHERE id = :id
            AND (
              status = 'QUEUED'
              OR (
                status = 'DEFERRED'
                AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              )
            )
          """,
      nativeQuery = true)
  int claimForDelivery(@Param("id") Long id, @Param("now") Instant now);

  @Modifying
  @Query(
      value =
          """
          UPDATE email_recipients
          SET status = 'UNKNOWN',
              next_attempt_at = NULL,
              updated_at = :now
          WHERE status = 'PROCESSING'
            AND last_attempt_at < :staleBefore
          """,
      nativeQuery = true)
  int recoverStale(
      @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
