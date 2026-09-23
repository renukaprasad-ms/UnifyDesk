package com.messaging.ads.repository;

import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.enums.AdProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdAccountHealthRepository extends JpaRepository<AdAccountHealth, Long> {

  Optional<AdAccountHealth> findByCompanyIdAndAdAccountId(
      Long companyId, Long adAccountId);

  Optional<AdAccountHealth> findByAdAccountIdAndProvider(
      Long adAccountId, AdProvider provider);
}
