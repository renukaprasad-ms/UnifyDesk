package com.messaging.ads.repository;

import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {

  Page<AdCampaign> findByCompanyId(Long companyId, Pageable pageable);

  Optional<AdCampaign> findByIdAndCompanyId(Long id, Long companyId);

  Page<AdCampaign> findByCompanyIdAndAdAccountId(
      Long companyId, Long adAccountId, Pageable pageable);

  List<AdCampaign> findByCompanyIdAndAdAccountId(Long companyId, Long adAccountId);

  Optional<AdCampaign> findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
      Long companyId, Long adAccountId, AdProvider provider, String name);

  Optional<AdCampaign> findByCompanyIdAndAdAccountIdAndProviderAndExternalCampaignId(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      String externalCampaignId);
}
