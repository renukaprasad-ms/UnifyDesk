package com.messaging.ads.repository;

import com.messaging.ads.entity.AdInsightBreakdown;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdInsightBreakdownRepository
    extends JpaRepository<AdInsightBreakdown, Long> {

  List<AdInsightBreakdown>
      findByCompanyIdAndAdAccountIdAndInsightLevelAndBreakdownTypeAndRangeStartAndRangeEndOrderBySpendDesc(
          Long companyId,
          Long adAccountId,
          AdInsightLevel insightLevel,
          AdInsightBreakdownType breakdownType,
          LocalDate rangeStart,
          LocalDate rangeEnd);

  void deleteByCompanyIdAndAdAccountIdAndProviderAndInsightLevelAndBreakdownTypeAndRangeStartAndRangeEnd(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      AdInsightLevel insightLevel,
      AdInsightBreakdownType breakdownType,
      LocalDate rangeStart,
      LocalDate rangeEnd);
}
