package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastOutbox;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastOutboxRepository extends JpaRepository<BroadcastOutbox, Long> {
  @Query(
      value =
          """
          select *
          from broadcast_outbox
          where status in ('PENDING', 'RETRY')
            and available_at <= :now
          order by id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BroadcastOutbox> findClaimableForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          update broadcast_outbox
          set status = 'RETRY',
              claimed_at = null,
              available_at = :now,
              updated_at = :now,
              last_error = 'Recovered stale outbox claim'
          where status = 'PROCESSING'
            and claimed_at < :staleBefore
          """,
      nativeQuery = true)
  int recoverStale(
      @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
