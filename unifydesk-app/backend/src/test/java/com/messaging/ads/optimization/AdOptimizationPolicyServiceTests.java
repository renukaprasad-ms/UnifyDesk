package com.messaging.ads.optimization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsCampaignMutationService;
import com.messaging.ads.meta.MetaAdsCampaignMutationService;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.ads.service.AdAnalyticsService;
import com.messaging.common.exception.BadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdOptimizationPolicyServiceTests {

  @Mock private AdOptimizationPolicyRepository repository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdAnalyticsService analyticsService;
  @Mock private GoogleAdsCampaignMutationService googleCampaignMutationService;
  @Mock private MetaAdsCampaignMutationService metaCampaignMutationService;
  @Mock private AdActivityLogService activityLogService;

  private AdOptimizationPolicyService service;

  @BeforeEach
  void setUp() {
    service =
        new AdOptimizationPolicyService(
            repository,
            accountRepository,
            campaignRepository,
            analyticsService,
            googleCampaignMutationService,
            metaCampaignMutationService,
            activityLogService,
            new ObjectMapper());
  }

  @Test
  void rejectsAutomaticIrreversibleBudgetAction() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));

    assertThatThrownBy(
            () ->
                service.create(
                    10L,
                    77L,
                    new AdOptimizationPolicyRequest(
                        "Do not auto-move money",
                        "AUTOMATION_RULE",
                        "AUTOMATIC",
                        AdProvider.GOOGLE_ADS,
                        20L,
                        null,
                        """
                        {
                          "metric":"ROAS",
                          "operator":"LT",
                          "threshold":1.5,
                          "minimumConversions":10,
                          "cooldownHours":24,
                          "maxDailyBudgetMovementPercent":20,
                          "action":"RECOMMEND_BUDGET_CHANGE"
                        }
                        """,
                        true)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("reversible");
  }
}
