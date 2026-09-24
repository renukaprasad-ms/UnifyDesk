package com.messaging.ads.repository;

import com.messaging.ads.entity.Ad;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdRepository extends JpaRepository<Ad, Long> {
  List<Ad> findByCompanyIdAndAdSetIdOrderByProviderUpdatedAtDesc(Long companyId, Long adSetId);
  List<Ad> findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(Long companyId, Long adAccountId);
  Optional<Ad> findByIdAndCompanyId(Long id, Long companyId);
  Optional<Ad> findByCompanyIdAndAdAccountIdAndProviderAndExternalAdId(
      Long companyId, Long adAccountId, AdProvider provider, String externalAdId);
}
