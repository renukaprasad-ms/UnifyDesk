package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, Long> {
  @Query(
      value =
          """
          select *
          from broadcast_recipients
          where batch_id = :batchId
            and status in ('PENDING', 'RETRY')
            and (next_attempt_at is null or next_attempt_at <= :now)
          order by id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BroadcastRecipient> findClaimableForUpdate(
      @Param("batchId") Long batchId,
      @Param("now") Instant now,
      @Param("limit") int limit);

  @Query(
      value =
          """
          select *
          from broadcast_recipients
          where status = 'PROCESSING'
            and claimed_at < :staleBefore
          order by claimed_at asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BroadcastRecipient> findStaleForUpdate(
      @Param("staleBefore") Instant staleBefore, @Param("limit") int limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select recipient from BroadcastRecipient recipient where recipient.id = :id")
  Optional<BroadcastRecipient> findByIdForUpdate(@Param("id") Long id);

  long countByBatchIdAndStatusIn(Long batchId, Collection<BroadcastRecipientStatus> statuses);

  long countByRunIdAndStatusIn(Long runId, Collection<BroadcastRecipientStatus> statuses);

  Optional<BroadcastRecipient> findFirstByConnectionIdAndInternetMessageId(
      Long connectionId, String internetMessageId);

  Optional<BroadcastRecipient> findByProviderCorrelationId(String providerCorrelationId);

  List<BroadcastRecipient>
      findTop2ByConnectionIdAndNormalizedDestinationAndStatusInAndCreatedAtAfterOrderByUpdatedAtDesc(
          Long connectionId,
          String normalizedDestination,
          Collection<BroadcastRecipientStatus> statuses,
          Instant createdAfter);

  @Query(
      """
      select min(recipient.nextAttemptAt)
      from BroadcastRecipient recipient
      where recipient.batchId = :batchId
        and recipient.status = com.messaging.broadcast.enums.BroadcastRecipientStatus.RETRY
      """)
  Instant findNextRetryAt(@Param("batchId") Long batchId);
}
