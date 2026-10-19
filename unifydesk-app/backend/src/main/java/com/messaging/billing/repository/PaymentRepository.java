package com.messaging.billing.repository;

import com.messaging.billing.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByInvoiceId(Long invoiceId);
  Optional<Payment> findByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);
  Optional<Payment> findByProviderAndProviderOrderId(String provider, String providerOrderId);
  Optional<Payment> findByProviderAndProviderCheckoutSessionId(String provider, String sessionId);

  @Query("select p from Payment p where p.providerOrderId = :orderId or p.providerCheckoutSessionId = :orderId")
  Optional<Payment> findByProviderOrderIdOrCheckoutSessionId(@Param("orderId") String orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.id = :id")
  Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
