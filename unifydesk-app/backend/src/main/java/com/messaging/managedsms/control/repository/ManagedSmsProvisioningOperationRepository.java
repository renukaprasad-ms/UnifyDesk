package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsProvisioningOperation;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ManagedSmsProvisioningOperationRepository extends JpaRepository<ManagedSmsProvisioningOperation, Long> {
  Optional<ManagedSmsProvisioningOperation> findByIdempotencyKey(String idempotencyKey);
  @Query(value = """
      select * from managed_sms_provisioning_operations
      where (status in ('PENDING','RETRY') and coalesce(next_attempt_at, now()) <= now())
         or (status = 'RUNNING' and lease_until < :now)
      order by coalesce(next_attempt_at, created_at), id
      limit 1 for update skip locked
      """, nativeQuery = true)
  Optional<ManagedSmsProvisioningOperation> findNextClaimable(@Param("now") Instant now);
}
