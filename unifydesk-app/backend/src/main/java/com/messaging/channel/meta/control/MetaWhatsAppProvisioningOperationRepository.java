package com.messaging.channel.meta.control;

import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation.OperationState;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaWhatsAppProvisioningOperationRepository
    extends JpaRepository<MetaWhatsAppProvisioningOperation, Long> {

  Optional<MetaWhatsAppProvisioningOperation> findByOperationKey(String operationKey);

  Optional<MetaWhatsAppProvisioningOperation> findFirstByConnectionIdOrderByUpdatedAtDesc(
      Long connectionId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update MetaWhatsAppProvisioningOperation operation
         set operation.claimToken = :claimToken,
             operation.claimExpiresAt = :claimExpiresAt,
             operation.state = :claimedState,
             operation.attemptCount = operation.attemptCount + 1,
             operation.updatedAt = :now
       where operation.connectionId = :connectionId
         and (operation.claimExpiresAt is null or operation.claimExpiresAt <= :now)
      """)
  int tryClaim(
      @Param("connectionId") Long connectionId,
      @Param("claimToken") String claimToken,
      @Param("claimExpiresAt") Instant claimExpiresAt,
      @Param("claimedState") OperationState claimedState,
      @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update MetaWhatsAppProvisioningOperation operation
         set operation.claimToken = null,
             operation.claimExpiresAt = null,
             operation.updatedAt = :now
       where operation.connectionId = :connectionId
         and operation.claimToken = :claimToken
      """)
  int releaseClaim(
      @Param("connectionId") Long connectionId,
      @Param("claimToken") String claimToken,
      @Param("now") Instant now);
}
