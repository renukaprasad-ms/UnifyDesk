package com.messaging.lifecycle.repository;

import com.messaging.lifecycle.entity.DeletionPurgeJob;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeletionPurgeJobRepository extends JpaRepository<DeletionPurgeJob, Long> {

  Optional<DeletionPurgeJob> findByEntityTypeAndEntityId(
      LifecycleEntityType entityType, Long entityId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from DeletionPurgeJob j where j.id = :id")
  Optional<DeletionPurgeJob> findByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          """
          select *
          from deletion_purge_jobs
          where status in ('PENDING', 'FAILED')
            and coalesce(next_attempt_at, purge_after) <= now()
          order by purge_after asc, created_at asc
          limit :limit
          for update skip locked
          """,
      nativeQuery = true)
  List<DeletionPurgeJob> findDueForUpdate(@Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          update deletion_purge_jobs
          set status = 'FAILED',
              next_attempt_at = now(),
              claimed_at = null,
              last_error = 'Recovered stale purge job',
              updated_at = now()
          where status = 'RUNNING'
            and claimed_at is not null
            and claimed_at < :cutoff
          """,
      nativeQuery = true)
  int recoverStale(@Param("cutoff") Instant cutoff);
}
