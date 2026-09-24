package com.messaging.emailprovider.mail.repository;

import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailRecipientRepository extends JpaRepository<EmailRecipient, Long> {

  List<EmailRecipient> findAllByMessageIdOrderByCreatedAtAsc(Long messageId);

  List<EmailRecipient> findAllByStatusOrderByCreatedAtAsc(EmailRecipientStatus status);

  @Query(
      """
      select r.id from EmailRecipient r
      where r.messageId = :messageId
        and (
          r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.QUEUED
          or (
            r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.DEFERRED
            and (r.nextAttemptAt is null or r.nextAttemptAt <= :now)
          )
        )
      order by r.createdAt
      """)
  List<Long> findDueRecipientIds(@Param("messageId") Long messageId, @Param("now") Instant now);

  @Query(
      """
      select distinct r.messageId from EmailRecipient r
      where r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.DEFERRED
        and r.nextAttemptAt <= :now
      """)
  List<Long> findMessageIdsWithDueRetries(@Param("now") Instant now);

  @Modifying
  @Query(
      """
      update EmailRecipient r
      set r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.PROCESSING,
          r.attemptCount = r.attemptCount + 1,
          r.lastAttemptAt = :now,
          r.nextAttemptAt = null
      where r.id = :id
        and (
          r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.QUEUED
          or (
            r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.DEFERRED
            and (r.nextAttemptAt is null or r.nextAttemptAt <= :now)
          )
        )
      """)
  int claimForDelivery(@Param("id") Long id, @Param("now") Instant now);

  @Modifying
  @Query(
      """
      update EmailRecipient r
      set r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.DEFERRED,
          r.nextAttemptAt = :now
      where r.status = com.messaging.emailprovider.mail.enums.EmailRecipientStatus.PROCESSING
        and r.lastAttemptAt < :staleBefore
      """)
  int recoverStale(
      @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
