package com.messaging.ads.repository;

import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdInsightDailyRepository extends JpaRepository<AdInsightDaily, Long> {

  Optional<AdInsightDaily>
      findByAdAccountIdAndProviderAndInsightLevelAndGranularityAndEntityRefAndInsightDateAndPeriodEnd(
          Long adAccountId,
          AdProvider provider,
          AdInsightLevel insightLevel,
          AdInsightGranularity granularity,
          String entityRef,
          LocalDate insightDate,
          LocalDate periodEnd);

  List<AdInsightDaily>
      findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
          Long companyId,
          Long adAccountId,
          AdInsightLevel insightLevel,
          AdInsightGranularity granularity,
          LocalDate from,
          LocalDate to);

  List<AdInsightDaily>
      findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
          Long companyId,
          Long adAccountId,
          AdInsightLevel insightLevel,
          AdInsightGranularity granularity,
          LocalDate from,
          LocalDate to);

  void deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndGranularityAndInsightDateBetween(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      AdInsightLevel insightLevel,
      AdInsightGranularity granularity,
      LocalDate from,
      LocalDate to);

  void deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      AdInsightLevel insightLevel,
      AdInsightGranularity granularity,
      LocalDate from,
      LocalDate to);
}
