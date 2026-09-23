package com.messaging.ads.repository;

import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdInsightDailyRepository extends JpaRepository<AdInsightDaily, Long> {

  Optional<AdInsightDaily>
      findByAdAccountIdAndProviderAndInsightLevelAndEntityRefAndInsightDate(
          Long adAccountId,
          AdProvider provider,
          AdInsightLevel insightLevel,
          String entityRef,
          LocalDate insightDate);

  List<AdInsightDaily> findByCompanyIdAndAdAccountIdAndInsightLevelAndInsightDateBetweenOrderByInsightDateAsc(
      Long companyId,
      Long adAccountId,
      AdInsightLevel insightLevel,
      LocalDate from,
      LocalDate to);

  void deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndInsightDateBetween(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      AdInsightLevel insightLevel,
      LocalDate from,
      LocalDate to);
}
