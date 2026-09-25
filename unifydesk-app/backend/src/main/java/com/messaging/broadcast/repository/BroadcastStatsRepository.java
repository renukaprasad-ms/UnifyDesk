package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastStats;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastStatsRepository extends JpaRepository<BroadcastStats, Long> {
  Optional<BroadcastStats> findByRunId(Long runId);

  @Modifying
  @Query(
      """
      update BroadcastStats stats
      set stats.totalCount = stats.totalCount + :totalDelta,
          stats.pendingCount = stats.pendingCount + :pendingDelta,
          stats.skippedCount = stats.skippedCount + :skippedDelta,
          stats.updatedAt = CURRENT_TIMESTAMP
      where stats.runId = :runId
      """)
  int addSnapshotCounts(
      @Param("runId") Long runId,
      @Param("totalDelta") long totalDelta,
      @Param("pendingDelta") long pendingDelta,
      @Param("skippedDelta") long skippedDelta);
}
