package com.messaging.ads.service;

import com.messaging.ads.repository.AdAccountResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdAccountResourceLifecycleService {

  private final AdAccountResourceRepository resourceRepository;

  @Transactional
  public void markAllInaccessible(Long companyId, Long adAccountId) {
    var resources = resourceRepository.findByCompanyIdAndAdAccountId(companyId, adAccountId);
    resources.forEach(resource -> resource.setAccessible(false));
    resourceRepository.saveAll(resources);
  }
}
