package com.messaging.ads.service;

import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdSetService {
  private final AdSetRepository adSetRepository;

  @Transactional(readOnly = true)
  public List<AdSetResponse> list(Long companyId, Long campaignId) {
    return adSetRepository
        .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(companyId, campaignId)
        .stream()
        .map(AdSetResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdSetResponse get(Long companyId, Long adSetId) {
    return adSetRepository
        .findByIdAndCompanyId(adSetId, companyId)
        .map(AdSetResponse::from)
        .orElseThrow(() -> new NotFoundException("Advertising ad set not found"));
  }
}
