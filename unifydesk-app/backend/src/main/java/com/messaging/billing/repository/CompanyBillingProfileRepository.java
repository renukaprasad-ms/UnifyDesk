package com.messaging.billing.repository;

import com.messaging.billing.entity.CompanyBillingProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyBillingProfileRepository extends JpaRepository<CompanyBillingProfile, Long> {

  Optional<CompanyBillingProfile> findByCompanyId(Long companyId);
}
