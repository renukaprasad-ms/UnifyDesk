package com.messaging.channel.gmail.repository;

import com.messaging.channel.gmail.entity.GmailSyncRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailSyncRunRepository extends JpaRepository<GmailSyncRun, Long> {

  Optional<GmailSyncRun> findByJobId(Long jobId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from GmailSyncRun r where r.jobId = :jobId")
  Optional<GmailSyncRun> findByJobIdForUpdate(@Param("jobId") Long jobId);
}
