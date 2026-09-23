package com.messaging.ads.service;

import com.messaging.ads.dto.AdKeywordResponse;
import com.messaging.ads.repository.AdKeywordRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdKeywordService {

  private final AdKeywordRepository repository;

  @Transactional(readOnly = true)
  public List<AdKeywordResponse> list(Long companyId, Long adSetId) {
    return repository.findByCompanyIdAndAdSetIdOrderByUpdatedAtDesc(companyId, adSetId).stream()
        .filter(keyword -> keyword.isAccessible())
        .map(AdKeywordResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdKeywordResponse get(Long companyId, Long keywordId) {
    return repository
        .findByIdAndCompanyId(keywordId, companyId)
        .filter(keyword -> keyword.isAccessible())
        .map(AdKeywordResponse::from)
        .orElseThrow(() -> new NotFoundException("Advertising keyword not found"));
  }
}
