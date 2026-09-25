package com.messaging.ads.repository;

import com.messaging.ads.entity.AdRecommendationState;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.enums.AdRecommendationLifecycleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdRecommendationStateRepository
    extends JpaRepository<AdRecommendationState, Long> {

  List<AdRecommendationState> findByCompanyIdAndAdAccountId(
      Long companyId, Long adAccountId);

  Optional<AdRecommendationState>
      findByCompanyIdAndAdAccountIdAndProviderAndRecommendationKey(
          Long companyId,
          Long adAccountId,
          AdProvider provider,
          String recommendationKey);

  long countByCompanyIdAndStatus(
      Long companyId, AdRecommendationLifecycleStatus status);
}
