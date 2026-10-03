package com.messaging.billing.repository;

import com.messaging.billing.entity.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
  List<PaymentAttempt> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);
  Optional<PaymentAttempt> findByProviderPaymentId(String providerPaymentId);
}
