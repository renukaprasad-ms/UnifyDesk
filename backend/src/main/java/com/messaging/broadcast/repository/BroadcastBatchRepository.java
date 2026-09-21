package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastBatch;
import com.messaging.broadcast.enums.BroadcastBatchStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastBatchRepository extends JpaRepository<BroadcastBatch, Long> {
  @Query(
      value =
          """
          select b.*
          from broadcast_batches b
          join broadcast_runs r on r.id = b.run_id
          where r.status = 'RUNNING'
            and b.status in ('PENDING', 'RETRY')
            and (b.next_attempt_at is null or b.next_attempt_at <= :now)
          order by b.id asc
          for update of b skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BroadcastBatch> findClaimableForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  long countByRunIdAndStatusIn(Long runId, Collection<BroadcastBatchStatus> statuses);

  List<BroadcastBatch> findByRunIdAndStatusIn(
      Long runId, Collection<BroadcastBatchStatus> statuses);
}
