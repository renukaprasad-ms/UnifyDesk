package com.messaging.ads.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.CreateCampaignChannelRequest;
import com.messaging.ads.campaign.dto.CreateReachlyoCampaignRequest;
import com.messaging.ads.campaign.dto.ReachlyoCampaignResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignSummaryResponse;
import com.messaging.ads.campaign.dto.UpdateCampaignChannelRequest;
import com.messaging.ads.campaign.dto.UpdateReachlyoCampaignRequest;
import com.messaging.ads.campaign.dto.UpsertBudgetPlanRequest;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import com.messaging.ads.campaign.enums.CampaignChannelStatus;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.BudgetPlanRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReachlyoCampaignService {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final ProviderDeploymentRepository deploymentRepository;
  private final BudgetPlanRepository budgetPlanRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public ReachlyoCampaignResponse create(
      Long companyId, Long userId, CreateReachlyoCampaignRequest request) {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setCompanyId(companyId);
    campaign.setName(request.name().trim());
    campaign.setObjective(request.objective());
    campaign.setBusinessGoal(request.businessGoal());
    campaign.setLandingPageUrl(normalizeNullableText(request.landingPageUrl()));
    campaign.setCurrency(normalizeCurrency(request.currency()));
    campaign.setStartAt(request.startAt());
    campaign.setEndAt(request.endAt());
    campaign.setCreatedByUserId(userId);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    validateSchedule(campaign);
    campaignRepository.save(campaign);
    return aggregate(campaign);
  }

  @Transactional(readOnly = true)
  public Page<ReachlyoCampaignSummaryResponse> list(Long companyId, Pageable pageable) {
    return campaignRepository
        .findByCompanyId(companyId, pageable)
        .map(ReachlyoCampaignSummaryResponse::from);
  }

  @Transactional(readOnly = true)
  public ReachlyoCampaignResponse get(Long companyId, Long campaignId) {
    return aggregate(requireCampaign(companyId, campaignId));
  }

  @Transactional
  public ReachlyoCampaignResponse update(
      Long companyId, Long campaignId, UpdateReachlyoCampaignRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireDefinitionEditable(campaign);

    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Campaign name cannot be blank");
      campaign.setName(request.name().trim());
    }
    if (request.objective() != null) campaign.setObjective(request.objective());
    if (request.businessGoal() != null) campaign.setBusinessGoal(request.businessGoal());
    if (request.landingPageUrl() != null) {
      campaign.setLandingPageUrl(normalizeNullableText(request.landingPageUrl()));
    }
    if (request.currency() != null) {
      if (deploymentRepository.existsByCompanyIdAndReachlyoCampaignId(companyId, campaignId)) {
        throw new ConflictException("Campaign currency cannot change after a provider deployment exists");
      }
      campaign.setCurrency(normalizeCurrency(request.currency()));
    }
    if (request.startAt() != null) campaign.setStartAt(request.startAt());
    if (request.endAt() != null) campaign.setEndAt(request.endAt());

    validateSchedule(campaign);
    campaignRepository.save(campaign);
    return aggregate(campaign);
  }

  @Transactional
  public ReachlyoCampaignResponse archive(Long companyId, Long campaignId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    if (campaign.getStatus() == ReachlyoCampaignStatus.ARCHIVED) return aggregate(campaign);
    campaign.setStatus(ReachlyoCampaignStatus.ARCHIVED);
    campaignRepository.save(campaign);
    return aggregate(campaign);
  }

  @Transactional
  public ReachlyoCampaignResponse addChannel(
      Long companyId, Long campaignId, CreateCampaignChannelRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireDefinitionEditable(campaign);

    if (channelRepository.existsByCompanyIdAndReachlyoCampaignIdAndChannelType(
        companyId, campaignId, request.channelType())) {
      throw new ConflictException("This channel is already configured for the campaign");
    }

    CampaignChannel channel = new CampaignChannel();
    channel.setCompanyId(companyId);
    channel.setReachlyoCampaignId(campaignId);
    channel.setChannelType(request.channelType());
    channel.setProvider(request.channelType().provider());
    channel.setEnabled(request.enabled() == null || request.enabled());
    channel.setBudgetAllocationMinor(request.budgetAllocationMinor());
    channel.setAllocationPercent(request.allocationPercent());
    channel.setProviderOverrideJson(normalizeJson(request.providerOverrideJson()));
    channel.setStatus(channel.isEnabled() ? CampaignChannelStatus.DRAFT : CampaignChannelStatus.DISABLED);

    validateExclusiveAllocation(channel);
    validateChannelAgainstBudget(companyId, campaignId, channel, null);
    channelRepository.save(channel);
    return aggregate(campaign);
  }

  @Transactional
  public ReachlyoCampaignResponse updateChannel(
      Long companyId,
      Long campaignId,
      Long channelId,
      UpdateCampaignChannelRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireDefinitionEditable(campaign);

    CampaignChannel channel =
        channelRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign channel not found"));

    if (request.enabled() != null) {
      channel.setEnabled(request.enabled());
      channel.setStatus(
          request.enabled() ? CampaignChannelStatus.DRAFT : CampaignChannelStatus.DISABLED);
    }
    if (request.budgetAllocationMinor() != null) {
      channel.setBudgetAllocationMinor(request.budgetAllocationMinor());
    }
    if (request.allocationPercent() != null) {
      channel.setAllocationPercent(request.allocationPercent());
    }
    if (request.providerOverrideJson() != null) {
      channel.setProviderOverrideJson(normalizeJson(request.providerOverrideJson()));
    }

    validateExclusiveAllocation(channel);
    validateChannelAgainstBudget(companyId, campaignId, channel, channelId);
    channelRepository.save(channel);
    return aggregate(campaign);
  }

  @Transactional
  public ReachlyoCampaignResponse removeChannel(
      Long companyId, Long campaignId, Long channelId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireDefinitionEditable(campaign);

    CampaignChannel channel =
        channelRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign channel not found"));

    if (deploymentRepository.existsByCompanyIdAndCampaignChannelId(companyId, channelId)) {
      throw new ConflictException("A channel with provider deployments cannot be removed");
    }
    channelRepository.delete(channel);
    return aggregate(campaign);
  }

  @Transactional
  public ReachlyoCampaignResponse upsertBudget(
      Long companyId, Long campaignId, UpsertBudgetPlanRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireDefinitionEditable(campaign);

    List<CampaignChannel> channels =
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(companyId, campaignId);

    normalizeAllocationsForMode(channels, request.allocationMode());
    validateBudgetTotals(channels, request.allocationMode(), request.totalBudgetMinor());

    BudgetPlan plan =
        budgetPlanRepository
            .findByCompanyIdAndReachlyoCampaignId(companyId, campaignId)
            .orElseGet(BudgetPlan::new);
    if (plan.getId() == null) {
      plan.setCompanyId(companyId);
      plan.setReachlyoCampaignId(campaignId);
    }
    plan.setTotalBudgetMinor(request.totalBudgetMinor());
    plan.setAllocationMode(request.allocationMode());
    budgetPlanRepository.save(plan);
    channelRepository.saveAll(channels);
    return aggregate(campaign);
  }

  @Transactional
  public ReachlyoCampaignResponse removeBudget(Long companyId, Long campaignId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireDefinitionEditable(campaign);

    budgetPlanRepository
        .findByCompanyIdAndReachlyoCampaignId(companyId, campaignId)
        .ifPresent(budgetPlanRepository::delete);

    List<CampaignChannel> channels =
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(companyId, campaignId);
    for (CampaignChannel channel : channels) {
      channel.setBudgetAllocationMinor(null);
      channel.setAllocationPercent(null);
    }
    channelRepository.saveAll(channels);
    return aggregate(campaign);
  }

  private ReachlyoCampaign requireCampaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private ReachlyoCampaignResponse aggregate(ReachlyoCampaign campaign) {
    Long companyId = campaign.getCompanyId();
    Long campaignId = campaign.getId();
    BudgetPlan budget =
        budgetPlanRepository
            .findByCompanyIdAndReachlyoCampaignId(companyId, campaignId)
            .orElse(null);
    return ReachlyoCampaignResponse.from(
        campaign,
        budget,
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(companyId, campaignId),
        deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
            companyId, campaignId));
  }

  private void requireDefinitionEditable(ReachlyoCampaign campaign) {
    if (campaign.getStatus() != ReachlyoCampaignStatus.DRAFT
        && campaign.getStatus() != ReachlyoCampaignStatus.READY) {
      throw new ConflictException(
          "Campaign definition can only be edited while the campaign is DRAFT or READY");
    }
  }

  private void validateSchedule(ReachlyoCampaign campaign) {
    if (campaign.getStartAt() != null
        && campaign.getEndAt() != null
        && !campaign.getEndAt().isAfter(campaign.getStartAt())) {
      throw new BadRequestException("Campaign end time must be after start time");
    }
  }

  private String normalizeCurrency(String currency) {
    return currency.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeNullableText(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String normalizeJson(String value) {
    String normalized = normalizeNullableText(value);
    if (normalized == null) return null;
    try {
      return objectMapper.writeValueAsString(objectMapper.readTree(normalized));
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Provider override must be valid JSON");
    }
  }

  private void validateExclusiveAllocation(CampaignChannel channel) {
    if (channel.getBudgetAllocationMinor() != null && channel.getAllocationPercent() != null) {
      throw new BadRequestException(
          "Choose either a fixed channel allocation or a percentage allocation, not both");
    }
  }

  private void validateChannelAgainstBudget(
      Long companyId, Long campaignId, CampaignChannel candidate, Long candidateId) {
    BudgetPlan plan =
        budgetPlanRepository
            .findByCompanyIdAndReachlyoCampaignId(companyId, campaignId)
            .orElse(null);
    if (plan == null) return;

    List<CampaignChannel> channels =
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(companyId, campaignId);
    channels =
        channels.stream()
            .filter(channel -> candidateId == null || !channel.getId().equals(candidateId))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    channels.add(candidate);
    validateModeFields(channels, plan.getAllocationMode());
    validateBudgetTotals(channels, plan.getAllocationMode(), plan.getTotalBudgetMinor());
  }

  private void validateModeFields(
      List<CampaignChannel> channels, BudgetAllocationMode allocationMode) {
    for (CampaignChannel channel : channels) {
      if (!channel.isEnabled()) continue;
      if (allocationMode == BudgetAllocationMode.AUTOMATIC
          && (channel.getBudgetAllocationMinor() != null || channel.getAllocationPercent() != null)) {
        throw new BadRequestException(
            "Automatic budget allocation cannot have manual channel allocations");
      }
      if (allocationMode == BudgetAllocationMode.FIXED && channel.getAllocationPercent() != null) {
        throw new BadRequestException(
            "Fixed budget allocation cannot use channel percentage values");
      }
      if (allocationMode == BudgetAllocationMode.PERCENTAGE
          && channel.getBudgetAllocationMinor() != null) {
        throw new BadRequestException(
            "Percentage budget allocation cannot use fixed channel amounts");
      }
    }
  }

  private void validateBudgetTotals(
      List<CampaignChannel> channels, BudgetAllocationMode mode, Long totalBudgetMinor) {
    validateModeFields(channels, mode);
    if (mode == BudgetAllocationMode.FIXED) {
      long allocated =
          channels.stream()
              .filter(CampaignChannel::isEnabled)
              .map(CampaignChannel::getBudgetAllocationMinor)
              .filter(java.util.Objects::nonNull)
              .mapToLong(Long::longValue)
              .sum();
      if (allocated > totalBudgetMinor) {
        throw new BadRequestException("Fixed channel allocations exceed the campaign budget");
      }
    }
    if (mode == BudgetAllocationMode.PERCENTAGE) {
      BigDecimal allocated =
          channels.stream()
              .filter(CampaignChannel::isEnabled)
              .map(CampaignChannel::getAllocationPercent)
              .filter(java.util.Objects::nonNull)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (allocated.compareTo(ONE_HUNDRED) > 0) {
        throw new BadRequestException("Channel allocation percentages cannot exceed 100%");
      }
    }
  }

  private void normalizeAllocationsForMode(
      List<CampaignChannel> channels, BudgetAllocationMode mode) {
    for (CampaignChannel channel : channels) {
      if (mode == BudgetAllocationMode.AUTOMATIC) {
        channel.setBudgetAllocationMinor(null);
        channel.setAllocationPercent(null);
      } else if (mode == BudgetAllocationMode.FIXED) {
        channel.setAllocationPercent(null);
      } else if (mode == BudgetAllocationMode.PERCENTAGE) {
        channel.setBudgetAllocationMinor(null);
      }
    }
  }
}
