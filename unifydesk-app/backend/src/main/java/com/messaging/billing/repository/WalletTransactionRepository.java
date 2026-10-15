package com.messaging.billing.repository;

import com.messaging.billing.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

  Page<WalletTransaction> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

  boolean existsByReferenceId(String referenceId);
}
