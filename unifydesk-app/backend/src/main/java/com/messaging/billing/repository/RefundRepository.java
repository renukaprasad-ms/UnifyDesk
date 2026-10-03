package com.messaging.billing.repository;

import com.messaging.billing.entity.Refund;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {
  Optional<Refund> findByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);
  Optional<Refund> findByProviderRefundId(String providerRefundId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Refund r where r.id = :id")
  Optional<Refund> findByIdForUpdate(@Param("id") Long id);
}
