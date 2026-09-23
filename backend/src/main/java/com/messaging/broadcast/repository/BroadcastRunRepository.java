package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastRunRepository extends JpaRepository<BroadcastRun, Long> {
  Optional<BroadcastRun> findByBroadcastMessageId(Long broadcastMessageId);

  Optional<BroadcastRun> findByIdAndCompanyId(Long id, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select run from BroadcastRun run where run.id = :id")
  Optional<BroadcastRun> findByIdForUpdate(@Param("id") Long id);
}
