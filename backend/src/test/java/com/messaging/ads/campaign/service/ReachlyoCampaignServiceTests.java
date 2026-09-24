package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.CreateCampaignChannelRequest;
import com.messaging.ads.campaign.dto.CreateReachlyoCampaignRequest;
import com.messaging.ads.campaign.dto.UpsertBudgetPlanRequest;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.BudgetPlanRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.enums.AdProvider;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReachlyoCampaignServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long USER_ID = 501L;

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private CampaignChannelRepository channelRepository;
  @Mock private ProviderDeploymentRepository deploymentRepository;
  @Mock private BudgetPlanRepository budgetPlanRepository;

  private ReachlyoCampaignService service;

  @BeforeEach
  void setUp() {
    service =
        new ReachlyoCampaignService(
            campaignRepository,
            channelRepository,
            deploymentRepository,
            budgetPlanRepository,
            new ObjectMapper());
  }

  @Test
  void createsDraftCampaignAndNormalizesCurrency() {
    when(campaignRepository.save(any(ReachlyoCampaign.class)))
        .thenAnswer(
            invocation -> {
              ReachlyoCampaign campaign = invocation.getArgument(0);
              campaign.setId(11L);
              return campaign;
            });
    when(channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 11L))
        .thenReturn(List.of());
    when(deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 11L))
        .thenReturn(List.of());
    when(budgetPlanRepository.findByCompanyIdAndReachlyoCampaignId(COMPANY_ID, 11L))
        .thenReturn(Optional.empty());

    var response =
        service.create(
            COMPANY_ID,
            USER_ID,
            new CreateReachlyoCampaignRequest(
                "Lead campaign",
                ReachlyoCampaignObjective.LEADS,
                CampaignBusinessGoal.QUALIFIED_LEAD,
                "https://example.com/leads",
                "inr",
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-31T23:59:00Z")));

    assertThat(response.status()).isEqualTo(ReachlyoCampaignStatus.DRAFT);
    assertThat(response.currency()).isEqualTo("INR");
    assertThat(response.createdByUserId()).isEqualTo(USER_ID.toString());
  }

  @Test
  void channelTypeControlsProvider() {
    ReachlyoCampaign campaign = draftCampaign(77L);
    when(campaignRepository.findByIdAndCompanyId(77L, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(channelRepository.existsByCompanyIdAndReachlyoCampaignIdAndChannelType(
            COMPANY_ID, 77L, CampaignChannelType.GOOGLE_SEARCH))
        .thenReturn(false);
    when(channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 77L))
        .thenReturn(List.of());
    when(deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 77L))
        .thenReturn(List.of());
    when(budgetPlanRepository.findByCompanyIdAndReachlyoCampaignId(COMPANY_ID, 77L))
        .thenReturn(Optional.empty());
    when(channelRepository.save(any(CampaignChannel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.addChannel(
        COMPANY_ID,
        77L,
        new CreateCampaignChannelRequest(
            CampaignChannelType.GOOGLE_SEARCH, true, null, null, "{\"aiMax\":false}"));

    ArgumentCaptor<CampaignChannel> captor = ArgumentCaptor.forClass(CampaignChannel.class);
    verify(channelRepository).save(captor.capture());
    assertThat(captor.getValue().getProvider()).isEqualTo(AdProvider.GOOGLE_ADS);
    assertThat(captor.getValue().getProviderOverrideJson()).isEqualTo("{\"aiMax\":false}");
  }

  @Test
  void rejectsFixedAllocationsThatExceedCampaignBudget() {
    ReachlyoCampaign campaign = draftCampaign(77L);
    BudgetPlan plan = new BudgetPlan();
    plan.setId(90L);
    plan.setCompanyId(COMPANY_ID);
    plan.setReachlyoCampaignId(77L);
    plan.setTotalBudgetMinor(10_000L);
    plan.setAllocationMode(BudgetAllocationMode.FIXED);

    CampaignChannel existing = new CampaignChannel();
    existing.setId(1L);
    existing.setCompanyId(COMPANY_ID);
    existing.setReachlyoCampaignId(77L);
    existing.setChannelType(CampaignChannelType.META_ADS);
    existing.setProvider(AdProvider.META_ADS);
    existing.setEnabled(true);
    existing.setBudgetAllocationMinor(8_000L);

    when(campaignRepository.findByIdAndCompanyId(77L, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(budgetPlanRepository.findByCompanyIdAndReachlyoCampaignId(COMPANY_ID, 77L))
        .thenReturn(Optional.of(plan));
    when(channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 77L))
        .thenReturn(List.of(existing));

    assertThatThrownBy(
            () ->
                service.addChannel(
                    COMPANY_ID,
                    77L,
                    new CreateCampaignChannelRequest(
                        CampaignChannelType.GOOGLE_SEARCH, true, 3_000L, null, null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("exceed");
  }

  @Test
  void switchingBudgetToAutomaticClearsManualChannelAllocations() {
    ReachlyoCampaign campaign = draftCampaign(77L);
    CampaignChannel channel = new CampaignChannel();
    channel.setId(1L);
    channel.setCompanyId(COMPANY_ID);
    channel.setReachlyoCampaignId(77L);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setProvider(AdProvider.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);

    when(campaignRepository.findByIdAndCompanyId(77L, COMPANY_ID))
        .thenReturn(Optional.of(campaign));
    when(channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 77L))
        .thenReturn(List.of(channel));
    when(budgetPlanRepository.findByCompanyIdAndReachlyoCampaignId(COMPANY_ID, 77L))
        .thenReturn(Optional.empty());
    when(budgetPlanRepository.save(any(BudgetPlan.class)))
        .thenAnswer(
            invocation -> {
              BudgetPlan plan = invocation.getArgument(0);
              plan.setId(99L);
              return plan;
            });
    when(deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(COMPANY_ID, 77L))
        .thenReturn(List.of());

    service.upsertBudget(
        COMPANY_ID, 77L, new UpsertBudgetPlanRequest(10_000L, BudgetAllocationMode.AUTOMATIC));

    assertThat(channel.getBudgetAllocationMinor()).isNull();
    assertThat(channel.getAllocationPercent()).isNull();
    verify(channelRepository).saveAll(List.of(channel));
  }

  private ReachlyoCampaign draftCampaign(Long id) {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(id);
    campaign.setCompanyId(COMPANY_ID);
    campaign.setName("Campaign");
    campaign.setObjective(ReachlyoCampaignObjective.LEADS);
    campaign.setBusinessGoal(CampaignBusinessGoal.LEAD);
    campaign.setCurrency("INR");
    campaign.setCreatedByUserId(USER_ID);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    return campaign;
  }
}
