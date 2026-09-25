package com.messaging.ads.service;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.repository.AdAccountBillingRepository;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdAccountBillingService {

  private final AdAccountRepository accountRepository;
  private final AdAccountBillingRepository billingRepository;

  @Transactional(readOnly = true)
  public AdAccountBillingResponse get(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    return billingRepository
        .findByCompanyIdAndAdAccountId(companyId, accountId)
        .map(AdAccountBillingResponse::from)
        .orElseGet(() -> AdAccountBillingResponse.empty(account));
  }
}
