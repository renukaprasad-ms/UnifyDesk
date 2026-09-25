package com.messaging.ads.service;

import com.messaging.ads.dto.AdCreativeResponse;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdCreativeService {
  private final AdCreativeRepository repository;

  @Transactional(readOnly = true)
  public List<AdCreativeResponse> list(Long companyId, Long accountId) {
    return repository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(companyId, accountId)
        .stream().map(AdCreativeResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public AdCreativeResponse get(Long companyId, Long creativeId) {
    return repository.findByIdAndCompanyId(creativeId, companyId)
        .map(AdCreativeResponse::from)
        .orElseThrow(() -> new NotFoundException("Advertising creative not found"));
  }
}
