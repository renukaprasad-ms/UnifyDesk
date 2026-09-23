package com.messaging.ads.repository;

import com.messaging.ads.entity.AdKeyword;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdKeywordRepository extends JpaRepository<AdKeyword, Long> {

  List<AdKeyword> findByCompanyIdAndAdSetIdOrderByUpdatedAtDesc(
      Long companyId, Long adSetId);

  Optional<AdKeyword> findByIdAndCompanyId(Long id, Long companyId);

  Optional<AdKeyword> findByCompanyIdAndAdAccountIdAndProviderAndExternalKeywordId(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      String externalKeywordId);
}
