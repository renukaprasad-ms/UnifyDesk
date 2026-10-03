package com.messaging.billing.repository;

import com.messaging.billing.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
  List<PaymentAttempt> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);

  Optional<PaymentAttempt> findByProviderPaymentId(String providerPaymentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PaymentAttempt a where a.id = :id")
  Optional<PaymentAttempt> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PaymentAttempt a where a.providerCommandId = :commandId")
  Optional<PaymentAttempt> findByProviderCommandIdForUpdate(
      @Param("commandId") String commandId);
}
