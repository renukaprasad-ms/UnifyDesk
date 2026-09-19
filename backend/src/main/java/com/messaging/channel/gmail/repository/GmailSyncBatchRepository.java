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

  long countByJobIdAndStatusIn(Long jobId, Collection<GmailSyncBatchStatus> statuses);

  long countByJobIdAndStatus(Long jobId, GmailSyncBatchStatus status);

  @Query(
      "select b from GmailSyncBatch b where b.status in :statuses "
          + "and b.updatedAt < :cutoff order by b.updatedAt asc")
  List<GmailSyncBatch> findRecoverable(
      @Param("statuses") Collection<GmailSyncBatchStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);
}
