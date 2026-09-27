package com.messaging.ads.optimization;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsExperimentService;
import com.messaging.ads.meta.MetaAdsExperimentService;
import com.messaging.ads.optimization.dto.AdExperimentOverviewResponse;
import com.messaging.ads.optimization.dto.AdExperimentOverviewResponse.Experiment;
import com.messaging.ads.optimization.dto.AdExperimentOverviewResponse.ProviderFailure;
import com.messaging.ads.repository.AdAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdExperimentOverviewService {

  private final AdAccountRepository accountRepository;
  private final GoogleAdsExperimentService googleExperimentService;
  private final MetaAdsExperimentService metaExperimentService;

  public AdExperimentOverviewResponse overview(Long companyId) {
    List<Experiment> experiments = new ArrayList<>();
    List<ProviderFailure> failures = new ArrayList<>();

    for (AdAccount account :
        accountRepository.findByCompanyIdOrderByProviderAscNameAsc(companyId)) {
      if (!account.isManaged() || !account.isAccessible()) continue;
      try {
        if (account.getProvider() == AdProvider.GOOGLE_ADS) {
          googleExperimentService.list(companyId, account.getId()).forEach(
              value -> experiments.add(
                  new Experiment(
                      AdProvider.GOOGLE_ADS,
                      account.getId().toString(),
                      value.id(),
                      value.name(),
                      value.type(),
                      value.status(),
                      "API_MANAGED",
                      value.startDate(),
                      value.endDate(),
                      value.metrics().impressions(),
                      value.metrics().controlImpressions(),
                      value.metrics().clicks(),
                      value.metrics().controlClicks(),
                      value.metrics().costMicros(),
                      value.metrics().controlCostMicros(),
                      nz(value.metrics().conversions()),
                      nz(value.metrics().controlConversions()),
                      nz(value.metrics().conversionValue()),
                      nz(value.metrics().controlConversionValue()))));
        } else if (account.getProvider() == AdProvider.META_ADS) {
          metaExperimentService.studies(companyId, account.getId()).forEach(
              value -> experiments.add(
                  new Experiment(
                      AdProvider.META_ADS,
                      account.getId().toString(),
                      value.id(),
                      value.name(),
                      value.type(),
                      "DISCOVERED",
                      "READ_ONLY",
                      epoch(value.startTime()),
                      epoch(value.endTime()),
                      0L,
                      0L,
                      0L,
                      0L,
                      0L,
                      0L,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO)));
        }
      } catch (RuntimeException exception) {
        failures.add(
            new ProviderFailure(
                account.getProvider(),
                account.getId().toString(),
                safeMessage(exception)));
      }
    }

    return new AdExperimentOverviewResponse(
        List.copyOf(experiments), List.copyOf(failures));
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String epoch(Long value) {
    if (value == null || value <= 0L) return null;
    return Instant.ofEpochSecond(value).toString();
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return "Provider experiment discovery failed";
    }
    return message.length() <= 500 ? message : message.substring(0, 500);
  }
}
