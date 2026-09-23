package com.messaging.ads.repository;

import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.enums.AdProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdAccountBillingRepository extends JpaRepository<AdAccountBilling, Long> {

  Optional<AdAccountBilling> findByCompanyIdAndAdAccountId(
      Long companyId, Long adAccountId);

  Optional<AdAccountBilling> findByAdAccountIdAndProvider(
      Long adAccountId, AdProvider provider);
}
