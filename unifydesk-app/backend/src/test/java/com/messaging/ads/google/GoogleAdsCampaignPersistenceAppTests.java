package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsCampaignPersistenceAppTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdCampaignRepository campaignRepository;

  @Test
  void normalizesMultiChannelAppSubtypeToAppObjective() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    GoogleAdsCampaignPersistenceService service =
        new GoogleAdsCampaignPersistenceService(
            accountRepository, campaignRepository, objectMapper);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    account.setCurrency("INR");

    when(accountRepository.findByIdAndCompanyIdForUpdate(20L, 10L))
        .thenReturn(Optional.of(account));
    when(
            campaignRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalCampaignId(
                    10L, 20L, AdProvider.GOOGLE_ADS, "777"))
        .thenReturn(Optional.empty());
    when(campaignRepository.save(org.mockito.ArgumentMatchers.any(AdCampaign.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var row =
        objectMapper.readTree(
            """
            {
              "campaign":{
                "id":"777",
                "name":"Engagement",
                "status":"PAUSED",
                "advertisingChannelType":"MULTI_CHANNEL",
                "advertisingChannelSubType":"APP_CAMPAIGN_FOR_ENGAGEMENT",
                "biddingStrategyType":"TARGET_CPA"
              },
              "campaignBudget":{"amountMicros":"50000000"}
            }
            """);

    AdCampaign stored = service.persistOne(10L, 20L, row);

    assertThat(stored.getObjective()).isEqualTo("APP");
    assertThat(stored.getBuyingType()).isEqualTo("APP_CAMPAIGN_FOR_ENGAGEMENT");
    assertThat(stored.getBidStrategy()).isEqualTo("TARGET_CPA");
    assertThat(stored.getDailyBudgetMinor()).isNotNull();
  }
}
