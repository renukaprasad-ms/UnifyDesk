package com.messaging.ads.service;

import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdCampaignService {

  private final AdCampaignRepository campaignRepository;

  @Transactional(readOnly = true)
  public Page<AdCampaignResponse> list(
      Long companyId, Long adAccountId, Pageable pageable) {
    if (adAccountId == null) {
      return campaignRepository.findByCompanyId(companyId, pageable).map(AdCampaignResponse::from);
    }
    return campaignRepository
        .findByCompanyIdAndAdAccountId(companyId, adAccountId, pageable)
        .map(AdCampaignResponse::from);
  }
}
