package com.messaging.emailprovider.validation.repository;

import com.messaging.emailprovider.validation.entity.EmailValidationBatch;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailValidationBatchRepository extends JpaRepository<EmailValidationBatch, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from EmailValidationBatch b where b.id = :id")
  Optional<EmailValidationBatch> findLockedById(@Param("id") Long id);

  @Query(
      value =
          """
          SELECT * FROM email_validation_batches
          WHERE status = 'RETRY'
            AND next_attempt_at <= :now
          ORDER BY next_attempt_at, id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<EmailValidationBatch> findDueRetries(
      @Param("now") Instant now, @Param("limit") int limit);
}
