package com.messaging.ads.repository;

import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdSetRepository extends JpaRepository<AdSet, Long> {
  List<AdSet> findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(
      Long companyId, Long campaignId);

  Optional<AdSet> findByIdAndCompanyId(Long id, Long companyId);

  Optional<AdSet> findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
      Long companyId, Long adAccountId, AdProvider provider, String externalAdSetId);
}
