package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailProvisioningOperation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedEmailProvisioningOperationRepository
    extends JpaRepository<ManagedEmailProvisioningOperation, Long> {

  Optional<ManagedEmailProvisioningOperation> findByIdempotencyKey(String idempotencyKey);

  @Query(
      value =
          """
          select *
          from managed_email_provisioning_operations
          where (
              status in ('PENDING', 'RETRY')
              and coalesce(next_attempt_at, now()) <= now()
            )
            or (
              status = 'PROCESSING'
              and claimed_at < :staleBefore
            )
          order by coalesce(next_attempt_at, created_at), id
          limit 1
          for update skip locked
          """,
      nativeQuery = true)
  Optional<ManagedEmailProvisioningOperation> findNextClaimable(
      @Param("staleBefore") Instant staleBefore);
}
