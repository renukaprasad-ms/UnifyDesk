package com.messaging.ads.service;

import com.messaging.ads.dto.AdAnalyticsBreakdownResponse;
import com.messaging.ads.entity.AdInsightBreakdown;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdInsightBreakdownRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdAnalyticsBreakdownService {

  private final AdAccountRepository accountRepository;
  private final AdInsightBreakdownRepository breakdownRepository;

  @Transactional(readOnly = true)
  public AdAnalyticsBreakdownResponse breakdown(
      Long companyId,
      Long accountId,
      LocalDate from,
      LocalDate to,
      AdInsightBreakdownType type) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("A valid analytics date range is required");
    }

    accountRepository
        .findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));

    List<AdInsightBreakdown> rows =
        breakdownRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndBreakdownTypeAndRangeStartAndRangeEndOrderBySpendDesc(
                companyId,
                accountId,
                AdInsightLevel.ACCOUNT,
                type,
                from,
                to);

    Comparator<AdInsightBreakdown> comparator =
        type == AdInsightBreakdownType.ACTION_TYPE
                || type == AdInsightBreakdownType.ACTION_DEVICE
            ? Comparator.comparing(
                    AdInsightBreakdown::getActionValue,
                    Comparator.nullsFirst(java.math.BigDecimal::compareTo))
                .reversed()
            : Comparator.comparing(
                    AdInsightBreakdown::getSpend,
                    Comparator.nullsFirst(java.math.BigDecimal::compareTo))
                .reversed();

    List<AdAnalyticsBreakdownResponse.Row> responseRows =
        rows.stream()
            .sorted(comparator)
            .map(
                row ->
                    new AdAnalyticsBreakdownResponse.Row(
                        row.getDimensionValue(),
                        blank(row.getDimensionValue2()),
                        blank(row.getActionType()),
                        row.getCurrency(),
                        row.getSpend(),
                        row.getImpressions(),
                        row.getReach(),
                        row.getClicks(),
                        row.getCtr(),
                        row.getCpc(),
                        row.getCpm(),
                        row.getConversions(),
                        row.getConversionValue(),
                        row.getLeads(),
                        row.getPurchases(),
                        row.getActionValue()))
            .toList();

    Instant lastSyncedAt =
        rows.stream()
            .map(AdInsightBreakdown::getLastSyncAt)
            .filter(value -> value != null)
            .max(Instant::compareTo)
            .orElse(null);

    return new AdAnalyticsBreakdownResponse(type, responseRows, lastSyncedAt);
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
