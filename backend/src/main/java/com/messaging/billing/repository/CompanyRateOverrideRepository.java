package com.messaging.billing.repository;

import com.messaging.billing.entity.CompanyRateOverride;
import com.messaging.billing.enums.RateCardStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRateOverrideRepository extends JpaRepository<CompanyRateOverride, Long> {
  List<CompanyRateOverride> findByCompanyIdAndStatus(Long companyId, RateCardStatus status);

  List<CompanyRateOverride> findByCompanyIdOrderByEffectiveFromDesc(Long companyId);
}
