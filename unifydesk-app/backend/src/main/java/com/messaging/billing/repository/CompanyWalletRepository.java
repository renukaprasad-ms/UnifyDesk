package com.messaging.billing.repository;

import com.messaging.billing.entity.CompanyWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyWalletRepository extends JpaRepository<CompanyWallet, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM CompanyWallet w WHERE w.companyId = :companyId")
  Optional<CompanyWallet> findByCompanyIdForUpdate(@Param("companyId") Long companyId);

  Optional<CompanyWallet> findByCompanyId(Long companyId);
}
