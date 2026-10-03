package com.messaging.billing.repository;

import com.messaging.billing.entity.Refund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
  Optional<Refund> findByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);
  Optional<Refund> findByProviderRefundId(String providerRefundId);
}
