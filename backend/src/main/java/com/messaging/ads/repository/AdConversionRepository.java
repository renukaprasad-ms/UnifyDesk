package com.messaging.ads.repository;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdConversionRepository extends JpaRepository<AdConversion,Long>{
  List<AdConversion> findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(Long companyId, Long adAccountId);
  Optional<AdConversion> findByIdAndCompanyId(Long id, Long companyId);
  Optional<AdConversion> findByCompanyIdAndAdAccountIdAndProviderAndExternalConversionId(Long companyId, Long adAccountId, AdProvider provider, String externalConversionId);
}
