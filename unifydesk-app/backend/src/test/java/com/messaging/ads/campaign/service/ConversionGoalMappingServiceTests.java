package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.messaging.ads.campaign.dto.UpsertConversionGoalMappingRequest;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdConversionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionGoalMappingServiceTests {

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private ConversionGoalMappingRepository mappingRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdConversionRepository conversionRepository;

  private ConversionGoalMappingService service;

  @BeforeEach
  void setUp() {
    service =
        new ConversionGoalMappingService(
            campaignRepository, mappingRepository, accountRepository, conversionRepository);
  }

  @Test
  void normalizesSupportedMetaStandardEvent() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(11L);
    campaign.setCompanyId(1L);
    campaign.setBusinessGoal(CampaignBusinessGoal.SIGN_UP);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(11L, 1L)).thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(21L);
    account.setCompanyId(1L);
    account.setProvider(AdProvider.META_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    when(accountRepository.findByIdAndCompanyId(21L, 1L)).thenReturn(Optional.of(account));
    when(mappingRepository
            .findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
                1L, 11L, AdProvider.META_ADS, 21L, CampaignBusinessGoal.SIGN_UP))
        .thenReturn(Optional.empty());
    when(mappingRepository.save(any(ConversionGoalMapping.class)))
        .thenAnswer(
            invocation -> {
              ConversionGoalMapping mapping = invocation.getArgument(0);
              mapping.setId(41L);
              return mapping;
            });

    var response =
        service.upsert(
            1L,
            11L,
            new UpsertConversionGoalMappingRequest(
                AdProvider.META_ADS,
                21L,
                ConversionTargetType.STANDARD_EVENT,
                null,
                "complete_registration"));

    assertThat(response.providerEventName()).isEqualTo("COMPLETE_REGISTRATION");
    assertThat(response.validated()).isFalse();
  }

  @Test
  void rejectsUnsupportedMetaStandardEvent() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(12L);
    campaign.setCompanyId(1L);
    campaign.setBusinessGoal(CampaignBusinessGoal.APP_INSTALL);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(22L);
    account.setCompanyId(1L);
    account.setProvider(AdProvider.META_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    when(accountRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(account));
    when(mappingRepository
            .findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
                1L, 12L, AdProvider.META_ADS, 22L, CampaignBusinessGoal.APP_INSTALL))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.upsert(
                    1L,
                    12L,
                    new UpsertConversionGoalMappingRequest(
                        AdProvider.META_ADS,
                        22L,
                        ConversionTargetType.STANDARD_EVENT,
                        null,
                        "ACTIVATE_APP")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Unsupported Meta standard conversion event");
  }

  @Test
  void rejectsStandardEventForGoogle() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(13L);
    campaign.setCompanyId(1L);
    campaign.setBusinessGoal(CampaignBusinessGoal.LEAD);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(13L, 1L)).thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(23L);
    account.setCompanyId(1L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    when(accountRepository.findByIdAndCompanyId(23L, 1L)).thenReturn(Optional.of(account));
    when(mappingRepository
            .findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
                1L, 13L, AdProvider.GOOGLE_ADS, 23L, CampaignBusinessGoal.LEAD))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.upsert(
                    1L,
                    13L,
                    new UpsertConversionGoalMappingRequest(
                        AdProvider.GOOGLE_ADS,
                        23L,
                        ConversionTargetType.STANDARD_EVENT,
                        null,
                        "LEAD")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("only supported for Meta Ads");
  }

  @Test
  void validatesProviderConversionMapping() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setBusinessGoal(CampaignBusinessGoal.LEAD);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(1L);
    account.setConnectionId(5L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("123");
    account.setName("Google");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);
    when(accountRepository.findByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(account));

    AdConversion conversion = new AdConversion();
    conversion.setId(30L);
    conversion.setCompanyId(1L);
    conversion.setAdAccountId(20L);
    conversion.setProvider(AdProvider.GOOGLE_ADS);
    conversion.setExternalConversionId("customers/123/conversionActions/456");
    conversion.setName("Lead");
    conversion.setCustomEventType("SUBMIT_LEAD_FORM");
    conversion.setAccessible(true);
    when(conversionRepository.findByIdAndCompanyId(30L, 1L))
        .thenReturn(Optional.of(conversion));
    when(mappingRepository
            .findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
                1L, 10L, AdProvider.GOOGLE_ADS, 20L, CampaignBusinessGoal.LEAD))
        .thenReturn(Optional.empty());
    when(mappingRepository.save(any(ConversionGoalMapping.class)))
        .thenAnswer(
            invocation -> {
              ConversionGoalMapping mapping = invocation.getArgument(0);
              mapping.setId(40L);
              return mapping;
            });

    var response =
        service.upsert(
            1L,
            10L,
            new UpsertConversionGoalMappingRequest(
                AdProvider.GOOGLE_ADS,
                20L,
                ConversionTargetType.PROVIDER_CONVERSION,
                30L,
                null));

    assertThat(response.validated()).isTrue();
    assertThat(response.externalConversionId()).contains("conversionActions/456");
    assertThat(response.providerEventName()).isEqualTo("SUBMIT_LEAD_FORM");
  }
}
