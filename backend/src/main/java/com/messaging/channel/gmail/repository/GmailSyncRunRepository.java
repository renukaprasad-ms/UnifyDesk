package com.messaging.channel.gmail.repository;

import com.messaging.channel.gmail.entity.GmailSyncRun;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailSyncRunRepository extends JpaRepository<GmailSyncRun, Long> {

  Optional<GmailSyncRun> findByJobId(Long jobId);

  @Query(
      "select r from GmailSyncRun r where r.discoveryComplete = false "
          + "and r.finalized = false and r.updatedAt < :cutoff order by r.updatedAt asc")
  List<GmailSyncRun> findRecoverable(
      @Param("cutoff") Instant cutoff, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from GmailSyncRun r where r.jobId = :jobId")
  Optional<GmailSyncRun> findByJobIdForUpdate(@Param("jobId") Long jobId);
}
