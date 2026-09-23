package com.messaging.ads.service;
import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.repository.AdAudienceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class AdAudienceService {
  private final AdAudienceRepository repository;
  @Transactional(readOnly=true)
  public List<AdAudienceResponse> list(Long companyId, Long accountId){
    return repository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(companyId,accountId).stream().map(AdAudienceResponse::from).toList();
  }
}
