package com.messaging.ads.service;

import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.repository.AdRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdService {
  private final AdRepository repository;

  @Transactional(readOnly = true)
  public List<AdResponse> list(Long companyId, Long adSetId) {
    return repository.findByCompanyIdAndAdSetIdOrderByProviderUpdatedAtDesc(companyId, adSetId)
        .stream().map(AdResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<AdResponse> listByAccount(Long companyId, Long accountId) {
    return repository
        .findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(companyId, accountId)
        .stream()
        .map(AdResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdResponse get(Long companyId, Long adId) {
    return repository.findByIdAndCompanyId(adId, companyId)
        .map(AdResponse::from)
        .orElseThrow(() -> new NotFoundException("Advertising ad not found"));
  }
}
