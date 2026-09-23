package com.messaging.ads.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.repository.AdAccountHealthRepository;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdAccountHealthService {

  private final AdAccountRepository accountRepository;
  private final AdAccountHealthRepository healthRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public AdAccountHealthResponse get(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    return healthRepository
        .findByCompanyIdAndAdAccountId(companyId, accountId)
        .map(health -> AdAccountHealthResponse.from(health, objectMapper))
        .orElseGet(() -> AdAccountHealthResponse.empty(account));
  }
}
