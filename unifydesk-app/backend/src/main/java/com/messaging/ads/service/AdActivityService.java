package com.messaging.ads.service;

import com.messaging.ads.dto.AdActivityLogResponse;
import com.messaging.ads.repository.AdActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdActivityService {

  private final AdActivityLogRepository repository;

  @Transactional(readOnly = true)
  public Page<AdActivityLogResponse> list(
      Long companyId, Long accountId, Pageable pageable) {
    return (accountId == null
            ? repository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
            : repository.findByCompanyIdAndAdAccountIdOrderByCreatedAtDesc(
                companyId, accountId, pageable))
        .map(AdActivityLogResponse::from);
  }
}
