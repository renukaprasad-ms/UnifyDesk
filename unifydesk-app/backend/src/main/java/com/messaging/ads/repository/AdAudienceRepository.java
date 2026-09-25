package com.messaging.ads.repository;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdAudienceRepository extends JpaRepository<AdAudience,Long>{
  List<AdAudience> findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(Long companyId, Long adAccountId);
  Optional<AdAudience> findByIdAndCompanyId(Long id, Long companyId);
  Optional<AdAudience> findByCompanyIdAndAdAccountIdAndProviderAndExternalAudienceId(Long companyId, Long adAccountId, AdProvider provider, String externalAudienceId);
}
