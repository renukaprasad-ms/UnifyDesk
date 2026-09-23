package com.messaging.ads.service;

import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdAccountResourceService {

  private final AdAccountRepository accountRepository;
  private final AdAccountResourceRepository resourceRepository;

  @Transactional(readOnly = true)
  public List<AdAccountResourceResponse> list(Long companyId, Long adAccountId) {
    accountRepository
        .findByIdAndCompanyId(adAccountId, companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));

    return resourceRepository
        .findByCompanyIdAndAdAccountIdOrderByResourceTypeAscNameAsc(companyId, adAccountId)
        .stream()
        .map(AdAccountResourceResponse::from)
        .toList();
  }
}
