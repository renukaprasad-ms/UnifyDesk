package com.messaging.channel.gmail.repository;

import com.messaging.channel.gmail.entity.GmailSyncBatch;
import com.messaging.channel.gmail.enums.GmailSyncBatchStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailSyncBatchRepository extends JpaRepository<GmailSyncBatch, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from GmailSyncBatch b where b.id = :batchId")
  Optional<GmailSyncBatch> findByIdForUpdate(@Param("batchId") Long batchId);

  Optional<GmailSyncBatch> findByJobIdAndBatchIndex(Long jobId, int batchIndex);

  long countByJobIdAndStatusIn(Long jobId, Collection<GmailSyncBatchStatus> statuses);

  long countByJobIdAndStatus(Long jobId, GmailSyncBatchStatus status);

  @Query("select coalesce(sum(b.failedCount), 0) from GmailSyncBatch b where b.jobId = :jobId")
  long sumFailedCountByJobId(@Param("jobId") Long jobId);

  Optional<GmailSyncBatch> findFirstByJobIdAndLastErrorIsNotNullOrderByUpdatedAtDesc(Long jobId);

  @Query(
      value =
          """
          select b.*
          from gmail_sync_batches b
          join (
            select distinct on (candidate.connection_id) candidate.id
            from gmail_sync_batches candidate
            where candidate.status in ('PENDING', 'RETRY')
              and (candidate.next_attempt_at is null or candidate.next_attempt_at <= now())
              and not exists (
                select 1
                from gmail_sync_batches running
                where running.connection_id = candidate.connection_id
                  and running.status = 'RUNNING'
              )
              and not exists (
                select 1
                from gmail_sync_batches delayed
                where delayed.connection_id = candidate.connection_id
                  and delayed.status = 'RETRY'
                  and delayed.next_attempt_at > now()
              )
            order by candidate.connection_id, candidate.batch_index asc, candidate.created_at asc
          ) claimable on claimable.id = b.id
          order by b.updated_at asc, b.batch_index asc
          for update of b skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<GmailSyncBatch> findNextClaimableForUpdate(@Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          update gmail_sync_batches
          set next_attempt_at =
                case
                  when next_attempt_at is null or next_attempt_at < :eligibleAt
                    then :eligibleAt
                  else next_attempt_at
                end,
              updated_at = now()
          where id = (
            select id
            from gmail_sync_batches
            where connection_id = :connectionId
              and status = 'PENDING'
            order by batch_index asc, created_at asc
            limit 1
          )
          """,
      nativeQuery = true)
  int deferNextPendingBatch(
      @Param("connectionId") Long connectionId, @Param("eligibleAt") Instant eligibleAt);

  @Query(
      "select b from GmailSyncBatch b where b.status in :statuses "
          + "and b.updatedAt < :cutoff order by b.updatedAt asc")
  List<GmailSyncBatch> findRecoverable(
      @Param("statuses") Collection<GmailSyncBatchStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);
}
