package com.messaging.ads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.ads.dto.AdRecommendationsResponse;
import com.messaging.ads.dto.UpdateAdRecommendationStateRequest;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdRecommendationState;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.enums.AdRecommendationCategory;
import com.messaging.ads.enums.AdRecommendationLifecycleAction;
import com.messaging.ads.enums.AdRecommendationLifecycleStatus;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.enums.AdRecommendationSource;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdRecommendationStateRepository;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdRecommendationLifecycleServiceTests {

  @Mock private AdRecommendationService recommendationService;
  @Mock private AdRecommendationStateRepository stateRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdActivityLogService activityLogService;

  private AdRecommendationLifecycleService service;

  @BeforeEach
  void setUp() {
    service =
        new AdRecommendationLifecycleService(
            recommendationService,
            stateRepository,
            accountRepository,
            activityLogService);
  }

  @Test
  void criticalRecommendationCannotBeDismissed() {
    AdAccount account = account();
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(recommendationService.recommendations(10L, 20L))
        .thenReturn(raw(List.of(item("CRITICAL_ISSUE", AdRecommendationSeverity.CRITICAL))));
    when(
            stateRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndRecommendationKey(
                    10L, 20L, AdProvider.META_ADS, "CRITICAL_ISSUE"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updateState(
                    10L,
                    20L,
                    "CRITICAL_ISSUE",
                    new UpdateAdRecommendationStateRequest(
                        AdRecommendationLifecycleAction.DISMISS, null),
                    40L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Critical recommendations cannot be dismissed");
  }

  @Test
  void dismissingWarningSuppressesItFromActiveFeed() {
    AdAccount account = account();
    var warning = item("WARNING_ISSUE", AdRecommendationSeverity.WARNING);
    var raw = raw(List.of(warning));
    List<AdRecommendationState> states = new ArrayList<>();

    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(recommendationService.recommendations(10L, 20L)).thenReturn(raw);
    when(
            stateRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndRecommendationKey(
                    10L, 20L, AdProvider.META_ADS, "WARNING_ISSUE"))
        .thenReturn(Optional.empty());
    when(stateRepository.save(any(AdRecommendationState.class)))
        .thenAnswer(
            invocation -> {
              AdRecommendationState saved = invocation.getArgument(0);
              states.clear();
              states.add(saved);
              return saved;
            });
    when(stateRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenAnswer(ignored -> List.copyOf(states));

    AdRecommendationsResponse response =
        service.updateState(
            10L,
            20L,
            "WARNING_ISSUE",
            new UpdateAdRecommendationStateRequest(
                AdRecommendationLifecycleAction.DISMISS, null),
            40L);

    assertThat(response.items()).isEmpty();
    assertThat(response.dismissedCount()).isEqualTo(1);
    assertThat(response.suppressedItems())
        .singleElement()
        .extracting(AdRecommendationsResponse.Item::lifecycleStatus)
        .isEqualTo(AdRecommendationLifecycleStatus.DISMISSED);
    verify(activityLogService)
        .record(
            org.mockito.ArgumentMatchers.eq(10L),
            org.mockito.ArgumentMatchers.eq(20L),
            org.mockito.ArgumentMatchers.eq(AdProvider.META_ADS),
            org.mockito.ArgumentMatchers.eq(40L),
            org.mockito.ArgumentMatchers.eq("AD_RECOMMENDATION_DISMISSED"),
            org.mockito.ArgumentMatchers.eq("AD_RECOMMENDATION"),
            org.mockito.ArgumentMatchers.eq("WARNING_ISSUE"),
            any(),
            any(),
            any());
  }

  @Test
  void reconcileAutoResolvesRecommendationThatDisappeared() {
    AdAccount account = account();
    AdRecommendationState existing = new AdRecommendationState();
    existing.setCompanyId(10L);
    existing.setAdAccountId(20L);
    existing.setProvider(AdProvider.META_ADS);
    existing.setRecommendationKey("OLD_ISSUE");
    existing.setFingerprint("fingerprint");
    existing.setStatus(AdRecommendationLifecycleStatus.ACTIVE);
    existing.setLastSeenAt(Instant.now().minusSeconds(3600));

    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(recommendationService.recommendations(10L, 20L))
        .thenReturn(raw(List.of()));
    when(stateRepository.findByCompanyIdAndAdAccountId(10L, 20L))
        .thenReturn(List.of(existing));
    when(stateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AdRecommendationsResponse response = service.reconcile(10L, 20L, 40L);

    assertThat(response.items()).isEmpty();
    assertThat(existing.getStatus())
        .isEqualTo(AdRecommendationLifecycleStatus.RESOLVED);
    assertThat(existing.getResolvedAt()).isNotNull();
    verify(activityLogService)
        .record(
            org.mockito.ArgumentMatchers.eq(10L),
            org.mockito.ArgumentMatchers.eq(20L),
            org.mockito.ArgumentMatchers.eq(AdProvider.META_ADS),
            org.mockito.ArgumentMatchers.eq(40L),
            org.mockito.ArgumentMatchers.eq("AD_RECOMMENDATION_AUTO_RESOLVED"),
            org.mockito.ArgumentMatchers.eq("AD_RECOMMENDATION"),
            org.mockito.ArgumentMatchers.eq("OLD_ISSUE"),
            any(),
            any(),
            any());
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setName("Acme Ads");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }

  private AdRecommendationsResponse raw(
      List<AdRecommendationsResponse.Item> items) {
    return new AdRecommendationsResponse(
        "20",
        null,
        null,
        (int) items.stream().filter(item -> item.severity() == AdRecommendationSeverity.CRITICAL).count(),
        (int) items.stream().filter(item -> item.severity() == AdRecommendationSeverity.WARNING).count(),
        (int) items.stream().filter(item -> item.severity() == AdRecommendationSeverity.OPPORTUNITY).count(),
        (int) items.stream().filter(item -> item.severity() == AdRecommendationSeverity.INFO).count(),
        0,
        0,
        Instant.now(),
        null,
        null,
        null,
        items,
        List.of());
  }

  private AdRecommendationsResponse.Item item(
      String key, AdRecommendationSeverity severity) {
    return new AdRecommendationsResponse.Item(
        key,
        severity,
        AdRecommendationCategory.DELIVERY,
        AdRecommendationSource.REACHLYO_DIAGNOSTIC,
        "Recommendation " + key,
        "Description",
        "AD_ACCOUNT",
        "20",
        "Review",
        "/ad-center/settings",
        Map.of(),
        AdRecommendationLifecycleStatus.ACTIVE,
        null,
        severity != AdRecommendationSeverity.CRITICAL,
        true);
  }
}
