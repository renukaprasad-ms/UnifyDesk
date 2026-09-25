package com.messaging.ads.campaign.service;

import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.capability.AdFeatureCapabilityService;
import com.messaging.ads.campaign.compiler.ProviderCampaignCompileContext;
import com.messaging.ads.campaign.compiler.ProviderCampaignCompilerRegistry;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.dto.CompileReachlyoCampaignRequest;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.CreativePackAsset;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.BudgetPlanRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.CreativePackAssetRepository;
import com.messaging.ads.campaign.repository.CreativePackRepository;
import com.messaging.ads.campaign.repository.CreativeVariantRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignCompilationService {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final BudgetPlanRepository budgetPlanRepository;
  private final CreativePackRepository creativePackRepository;
  private final CreativePackAssetRepository creativePackAssetRepository;
  private final CreativeVariantRepository creativeVariantRepository;
  private final AudienceDefinitionRepository audienceDefinitionRepository;
  private final ConversionGoalMappingRepository conversionGoalMappingRepository;
  private final AdAccountRepository accountRepository;
  private final AdFeatureCapabilityService capabilityService;
  private final ProviderCampaignCompilerRegistry compilerRegistry;

  @Transactional(readOnly = true)
  public ProviderCampaignPlan compile(
      Long companyId,
      Long campaignId,
      Long channelId,
      CompileReachlyoCampaignRequest request) {
    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));

    CampaignChannel channel =
        channelRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign channel not found"));
    if (!channel.isEnabled()) {
      throw new BadRequestException("Disabled campaign channels cannot be compiled");
    }

    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(request.adAccountId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising account not found"));
    if (account.getProvider() != channel.getProvider()) {
      throw new BadRequestException("Advertising account provider does not match campaign channel");
    }
    if (!account.isAccessible()) {
      throw new BadRequestException("Advertising account is no longer accessible");
    }
    if (!account.isManaged()) {
      throw new BadRequestException("Advertising account is not enabled for Reachlyo management");
    }
    if (account.getStatus() == AdAccountStatus.DISABLED
        || account.getStatus() == AdAccountStatus.CLOSED) {
      throw new BadRequestException("Advertising account is not active");
    }

    BudgetPlan budgetPlan =
        budgetPlanRepository
            .findByCompanyIdAndReachlyoCampaignId(companyId, campaignId)
            .orElse(null);

    List<CampaignChannel> allChannels =
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(companyId, campaignId);

    CreativePack creativePack =
        resolveCreativePack(companyId, campaignId, request.creativePackId());
    List<CreativePackAsset> creativeAssets =
        creativePack == null
            ? List.of()
            : creativePackAssetRepository
                .findByCompanyIdAndCreativePackIdOrderBySortOrderAscIdAsc(
                    companyId, creativePack.getId());
    List<CreativeVariant> creativeVariants =
        creativePack == null
            ? List.of()
            : creativeVariantRepository.findByCompanyIdAndCreativePackIdOrderByCreatedAtAsc(
                companyId, creativePack.getId());

    AudienceDefinition audienceDefinition =
        resolveAudienceDefinition(companyId, campaignId, request.audienceDefinitionId());

    ConversionGoalMapping conversionGoalMapping =
        conversionGoalMappingRepository
            .findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
                companyId,
                campaignId,
                channel.getProvider(),
                account.getId(),
                campaign.getBusinessGoal())
            .orElse(null);

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        new EnumMap<>(AdFeatureCapability.class);
    for (AdFeatureCapabilityResponse capability :
        capabilityService.accountCapabilities(companyId, account.getId())) {
      capabilities.put(capability.feature(), capability);
    }

    ProviderCampaignCompileContext context =
        new ProviderCampaignCompileContext(
            campaign,
            channel,
            allChannels,
            account,
            budgetPlan,
            creativePack,
            creativeAssets,
            creativeVariants,
            audienceDefinition,
            conversionGoalMapping,
            Map.copyOf(capabilities));

    return compilerRegistry.require(channel.getChannelType()).compile(context);
  }

  private CreativePack resolveCreativePack(
      Long companyId, Long campaignId, Long requestedCreativePackId) {
    if (requestedCreativePackId != null) {
      return creativePackRepository
          .findByIdAndCompanyIdAndReachlyoCampaignId(
              requestedCreativePackId, companyId, campaignId)
          .orElseThrow(() -> new NotFoundException("Creative pack not found"));
    }

    List<CreativePack> packs =
        creativePackRepository.findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtAsc(
            companyId, campaignId);
    return packs.size() == 1 ? packs.getFirst() : null;
  }

  private AudienceDefinition resolveAudienceDefinition(
      Long companyId, Long campaignId, Long requestedAudienceDefinitionId) {
    if (requestedAudienceDefinitionId != null) {
      return audienceDefinitionRepository
          .findByIdAndCompanyIdAndReachlyoCampaignId(
              requestedAudienceDefinitionId, companyId, campaignId)
          .orElseThrow(() -> new NotFoundException("Audience definition not found"));
    }

    List<AudienceDefinition> definitions =
        audienceDefinitionRepository.findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtAsc(
            companyId, campaignId);
    return definitions.size() == 1 ? definitions.getFirst() : null;
  }
}
