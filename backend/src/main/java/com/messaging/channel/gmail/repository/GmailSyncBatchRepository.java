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
            select id,
                   row_number() over (
                     partition by connection_id
                     order by batch_index asc, created_at asc
                   ) as connection_rank
            from gmail_sync_batches
            where status in ('PENDING', 'RETRY')
          ) ranked on ranked.id = b.id
          where b.status in ('PENDING', 'RETRY')
          order by ranked.connection_rank asc, b.updated_at asc, b.batch_index asc
          for update of b skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<GmailSyncBatch> findNextClaimableForUpdate(@Param("limit") int limit);

  @Query(
      "select b from GmailSyncBatch b where b.status in :statuses "
          + "and b.updatedAt < :cutoff order by b.updatedAt asc")
  List<GmailSyncBatch> findRecoverable(
      @Param("statuses") Collection<GmailSyncBatchStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);
}
