package com.messaging.channel.microsoft.repository;

import com.messaging.channel.microsoft.entity.MicrosoftBackfillRun;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MicrosoftBackfillRunRepository
    extends JpaRepository<MicrosoftBackfillRun, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from MicrosoftBackfillRun r where r.jobId = :jobId")
  Optional<MicrosoftBackfillRun> findByJobIdForUpdate(@Param("jobId") Long jobId);

  @Query(
      value =
          """
          select r.*
          from microsoft_backfill_runs r
          join (
            select distinct on (candidate.connection_id) candidate.job_id
            from microsoft_backfill_runs candidate
            where candidate.status in ('PENDING', 'RETRY')
              and (candidate.next_attempt_at is null or candidate.next_attempt_at <= now())
              and not exists (
                select 1
                from microsoft_backfill_runs running
                where running.connection_id = candidate.connection_id
                  and running.status = 'RUNNING'
              )
            order by candidate.connection_id, candidate.updated_at asc
          ) claimable on claimable.job_id = r.job_id
          order by r.updated_at asc
          for update of r skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<MicrosoftBackfillRun> findClaimableForUpdate(@Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          update microsoft_backfill_runs
          set status = 'RETRY',
              claimed_at = null,
              next_attempt_at = now(),
              last_error = 'Recovered stale Microsoft backfill worker',
              updated_at = now()
          where status = 'RUNNING'
            and claimed_at is not null
            and claimed_at < :cutoff
          """,
      nativeQuery = true)
  int recoverStale(@Param("cutoff") Instant cutoff);
}
