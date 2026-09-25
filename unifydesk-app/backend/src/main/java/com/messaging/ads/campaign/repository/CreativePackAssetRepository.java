package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.CreativePackAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreativePackAssetRepository extends JpaRepository<CreativePackAsset, Long> {
  List<CreativePackAsset> findByCompanyIdAndCreativePackIdOrderBySortOrderAscIdAsc(Long companyId, Long creativePackId);
  void deleteByCompanyIdAndCreativePackId(Long companyId, Long creativePackId);
}
