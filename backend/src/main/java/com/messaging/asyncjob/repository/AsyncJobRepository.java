package com.messaging.asyncjob.repository;

import com.messaging.asyncjob.entity.AsyncJob;
import com.messaging.asyncjob.enums.AsyncJobStatus;
import com.messaging.asyncjob.enums.AsyncJobType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsyncJobRepository extends JpaRepository<AsyncJob, Long> {

  Optional<AsyncJob> findByIdAndUserId(Long id, Long userId);

  Optional<AsyncJob>
      findFirstByCompanyIdAndJobTypeAndResourceIdAndStatusInOrderByCreatedAtDesc(
          Long companyId,
          AsyncJobType jobType,
          String resourceId,
          Collection<AsyncJobStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from AsyncJob j where j.id = :jobId")
  Optional<AsyncJob> findByIdForUpdate(@Param("jobId") Long jobId);

  @Query(
      value =
          """
          select *
          from async_jobs
          where job_type = :jobType
            and (
              status = 'QUEUED'
              or (status = 'RUNNING' and updated_at < :staleCutoff)
            )
          order by created_at asc
          limit :limit
          for update skip locked
          """,
      nativeQuery = true)
  List<AsyncJob> findClaimableForUpdate(
      @Param("jobType") String jobType,
      @Param("staleCutoff") Instant staleCutoff,
      @Param("limit") int limit);
}
