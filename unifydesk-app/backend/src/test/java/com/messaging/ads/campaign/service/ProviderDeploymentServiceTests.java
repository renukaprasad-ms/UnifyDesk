package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderDeploymentServiceTests {

  private static final Long COMPANY_ID = 101L;

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private CampaignChannelRepository channelRepository;
  @Mock private ProviderDeploymentRepository deploymentRepository;
  @Mock private AdAccountRepository accountRepository;
  @Mock private AdCampaignRepository adCampaignRepository;

  private ProviderDeploymentService service;

  @BeforeEach
  void setUp() {
    service =
        new ProviderDeploymentService(
            campaignRepository,
            channelRepository,
            deploymentRepository,
            accountRepository,
            adCampaignRepository);
  }

  @Test
  void createsPendingDeploymentForMatchingManagedAccount() {
    ReachlyoCampaign campaign = campaign();
    CampaignChannel channel = channel(AdProvider.GOOGLE_ADS);
    AdAccount account = account(AdProvider.GOOGLE_ADS);

    when(campaignRepository.findByIdAndCompanyId(10L, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(channelRepository.findByIdAndCompanyIdAndReachlyoCampaignId(20L, COMPANY_ID, 10L))
        .thenReturn(Optional.of(channel));
    when(accountRepository.findByIdAndCompanyId(30L, COMPANY_ID))
        .thenReturn(Optional.of(account));
    when(deploymentRepository.findByCompanyIdAndCampaignChannelIdAndAdAccountId(
            COMPANY_ID, 20L, 30L))
        .thenReturn(Optional.empty());
    when(deploymentRepository.save(any(ProviderDeployment.class)))
        .thenAnswer(
            invocation -> {
              ProviderDeployment deployment = invocation.getArgument(0);
              deployment.setId(40L);
              return deployment;
            });

    ProviderDeployment deployment = service.createPending(COMPANY_ID, 10L, 20L, 30L);

    assertThat(deployment.getProvider()).isEqualTo(AdProvider.GOOGLE_ADS);
    assertThat(deployment.getDeploymentStatus()).isEqualTo(ProviderDeploymentStatus.PENDING);
  }

  @Test
  void rejectsAccountFromWrongProvider() {
    when(campaignRepository.findByIdAndCompanyId(10L, COMPANY_ID))
        .thenReturn(Optional.of(campaign()));
    when(channelRepository.findByIdAndCompanyIdAndReachlyoCampaignId(20L, COMPANY_ID, 10L))
        .thenReturn(Optional.of(channel(AdProvider.GOOGLE_ADS)));
    when(accountRepository.findByIdAndCompanyId(30L, COMPANY_ID))
        .thenReturn(Optional.of(account(AdProvider.META_ADS)));

    assertThatThrownBy(() -> service.createPending(COMPANY_ID, 10L, 20L, 30L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("provider");
  }

  private ReachlyoCampaign campaign() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(COMPANY_ID);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    return campaign;
  }

  private CampaignChannel channel(AdProvider provider) {
    CampaignChannel channel = new CampaignChannel();
    channel.setId(20L);
    channel.setCompanyId(COMPANY_ID);
    channel.setReachlyoCampaignId(10L);
    channel.setChannelType(
        provider == AdProvider.GOOGLE_ADS
            ? CampaignChannelType.GOOGLE_SEARCH
            : CampaignChannelType.META_ADS);
    channel.setProvider(provider);
    channel.setEnabled(true);
    return channel;
  }

  private AdAccount account(AdProvider provider) {
    AdAccount account = new AdAccount();
    account.setId(30L);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(99L);
    account.setProvider(provider);
    account.setExternalAccountId("account-30");
    account.setName("Account");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }
}
