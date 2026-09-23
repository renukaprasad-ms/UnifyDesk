package com.messaging.ads.repository;

import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdCreativeRepository extends JpaRepository<AdCreative, Long> {
  List<AdCreative> findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(
      Long companyId, Long adAccountId);
  Optional<AdCreative> findByIdAndCompanyId(Long id, Long companyId);
  Optional<AdCreative> findByCompanyIdAndAdAccountIdAndProviderAndExternalCreativeId(
      Long companyId, Long adAccountId, AdProvider provider, String externalCreativeId);
}
