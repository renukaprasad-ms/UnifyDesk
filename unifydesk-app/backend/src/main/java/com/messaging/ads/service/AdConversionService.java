package com.messaging.ads.service;
import com.messaging.ads.dto.AdConversionResponse;
import com.messaging.ads.repository.AdConversionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class AdConversionService {
  private final AdConversionRepository repository;
  @Transactional(readOnly=true)
  public List<AdConversionResponse> list(Long companyId, Long accountId){
    return repository.findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(companyId,accountId).stream().map(AdConversionResponse::from).toList();
  }
}
