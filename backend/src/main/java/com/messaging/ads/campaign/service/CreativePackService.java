package com.messaging.ads.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.CreateCreativePackRequest;
import com.messaging.ads.campaign.dto.CreateCreativeVariantRequest;
import com.messaging.ads.campaign.dto.CreativePackAssetInput;
import com.messaging.ads.campaign.dto.CreativePackResponse;
import com.messaging.ads.campaign.dto.CreativeVariantResponse;
import com.messaging.ads.campaign.dto.UpdateCreativePackRequest;
import com.messaging.ads.campaign.dto.UpdateCreativeVariantRequest;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.CreativePackAsset;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CreativeAssetRole;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.CreativePackAssetRepository;
import com.messaging.ads.campaign.repository.CreativePackRepository;
import com.messaging.ads.campaign.repository.CreativeVariantRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.enums.MediaType;
import com.messaging.media.repository.MediaRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreativePackService {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final CreativePackRepository packRepository;
  private final CreativePackAssetRepository assetRepository;
  private final CreativeVariantRepository variantRepository;
  private final MediaRepository mediaRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<CreativePackResponse> list(Long companyId, Long campaignId) {
    requireCampaign(companyId, campaignId);
    return packRepository.findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtAsc(companyId, campaignId)
        .stream()
        .map(pack -> response(companyId, pack))
        .toList();
  }

  @Transactional(readOnly = true)
  public CreativePackResponse get(Long companyId, Long campaignId, Long packId) {
    requireCampaign(companyId, campaignId);
    return response(companyId, requirePack(companyId, campaignId, packId));
  }

  @Transactional
  public CreativePackResponse create(
      Long companyId, Long userId, Long campaignId, CreateCreativePackRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);

    CreativePack pack = new CreativePack();
    pack.setCompanyId(companyId);
    pack.setReachlyoCampaignId(campaignId);
    pack.setCreatedByUserId(userId);
    apply(
        pack,
        request.name(),
        request.businessName(),
        request.offerText(),
        request.primaryMessage(),
        request.callToAction(),
        request.destinationUrl(),
        request.headlines(),
        request.descriptions());
    packRepository.save(pack);
    replaceAssets(companyId, pack.getId(), request.assets());
    return response(companyId, pack);
  }

  @Transactional
  public CreativePackResponse update(
      Long companyId, Long campaignId, Long packId, UpdateCreativePackRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    CreativePack pack = requirePack(companyId, campaignId, packId);

    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Creative pack name cannot be blank");
      pack.setName(request.name().trim());
    }
    if (request.businessName() != null) pack.setBusinessName(normalizeText(request.businessName()));
    if (request.offerText() != null) pack.setOfferText(normalizeText(request.offerText()));
    if (request.primaryMessage() != null) pack.setPrimaryMessage(normalizeText(request.primaryMessage()));
    if (request.callToAction() != null) pack.setCallToAction(normalizeText(request.callToAction()));
    if (request.destinationUrl() != null) pack.setDestinationUrl(normalizeText(request.destinationUrl()));
    if (request.headlines() != null) pack.setHeadlinesJson(json(request.headlines()));
    if (request.descriptions() != null) pack.setDescriptionsJson(json(request.descriptions()));
    packRepository.save(pack);

    if (request.assets() != null) replaceAssets(companyId, packId, request.assets());
    return response(companyId, pack);
  }

  @Transactional
  public void delete(Long companyId, Long campaignId, Long packId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    packRepository.delete(requirePack(companyId, campaignId, packId));
  }

  @Transactional
  public CreativeVariantResponse createVariant(
      Long companyId,
      Long campaignId,
      Long packId,
      CreateCreativeVariantRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    requirePack(companyId, campaignId, packId);
    validateChannel(companyId, campaignId, request.campaignChannelId(), request.variantType().provider());

    CreativeVariant variant = new CreativeVariant();
    variant.setCompanyId(companyId);
    variant.setReachlyoCampaignId(campaignId);
    variant.setCreativePackId(packId);
    variant.setCampaignChannelId(request.campaignChannelId());
    variant.setProvider(request.variantType().provider());
    variant.setVariantType(request.variantType());
    variant.setOrigin(request.origin());
    variant.setStatus(request.status());
    variant.setName(request.name().trim());
    variant.setPlacement(normalizeText(request.placement()));
    variant.setPrimaryText(normalizeText(request.primaryText()));
    variant.setHeadlinesJson(jsonNullable(request.headlines()));
    variant.setDescriptionsJson(jsonNullable(request.descriptions()));
    variant.setCallToAction(normalizeText(request.callToAction()));
    variant.setDestinationUrl(normalizeText(request.destinationUrl()));
    variant.setAssetSpecJson(normalizeJson(request.assetSpecJson()));
    variantRepository.save(variant);
    return CreativeVariantResponse.from(variant);
  }

  @Transactional
  public CreativeVariantResponse updateVariant(
      Long companyId,
      Long campaignId,
      Long packId,
      Long variantId,
      UpdateCreativeVariantRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    CreativeVariant variant =
        variantRepository
            .findByIdAndCompanyIdAndCreativePackId(variantId, companyId, packId)
            .orElseThrow(() -> new NotFoundException("Creative variant not found"));
    validateChannel(companyId, campaignId, request.campaignChannelId(), variant.getProvider());

    if (request.campaignChannelId() != null) variant.setCampaignChannelId(request.campaignChannelId());
    if (request.origin() != null) variant.setOrigin(request.origin());
    if (request.status() != null) variant.setStatus(request.status());
    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Creative variant name cannot be blank");
      variant.setName(request.name().trim());
    }
    if (request.placement() != null) variant.setPlacement(normalizeText(request.placement()));
    if (request.primaryText() != null) variant.setPrimaryText(normalizeText(request.primaryText()));
    if (request.headlines() != null) variant.setHeadlinesJson(json(request.headlines()));
    if (request.descriptions() != null) variant.setDescriptionsJson(json(request.descriptions()));
    if (request.callToAction() != null) variant.setCallToAction(normalizeText(request.callToAction()));
    if (request.destinationUrl() != null) variant.setDestinationUrl(normalizeText(request.destinationUrl()));
    if (request.assetSpecJson() != null) variant.setAssetSpecJson(normalizeJson(request.assetSpecJson()));
    variantRepository.save(variant);
    return CreativeVariantResponse.from(variant);
  }

  @Transactional
  public void deleteVariant(
      Long companyId, Long campaignId, Long packId, Long variantId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    CreativeVariant variant =
        variantRepository
            .findByIdAndCompanyIdAndCreativePackId(variantId, companyId, packId)
            .orElseThrow(() -> new NotFoundException("Creative variant not found"));
    if (!variant.getReachlyoCampaignId().equals(campaignId)) {
      throw new NotFoundException("Creative variant not found");
    }
    variantRepository.delete(variant);
  }

  private void replaceAssets(
      Long companyId, Long packId, List<CreativePackAssetInput> requestedAssets) {
    assetRepository.deleteByCompanyIdAndCreativePackId(companyId, packId);
    if (requestedAssets == null || requestedAssets.isEmpty()) return;

    List<CreativePackAsset> assets = new ArrayList<>();
    for (CreativePackAssetInput input : requestedAssets) {
      Media media =
          mediaRepository
              .findByIdAndCompanyId(input.mediaId(), companyId)
              .orElseThrow(() -> new NotFoundException("Creative media not found"));
      validateMedia(media, input.role());

      CreativePackAsset asset = new CreativePackAsset();
      asset.setCompanyId(companyId);
      asset.setCreativePackId(packId);
      asset.setMediaId(media.getId());
      asset.setRole(input.role());
      asset.setLabel(normalizeText(input.label()));
      asset.setSortOrder(input.sortOrder() == null ? assets.size() : input.sortOrder());
      assets.add(asset);
    }
    assetRepository.saveAll(assets);
  }

  private void validateMedia(Media media, CreativeAssetRole role) {
    if (media.getStatus() != MediaStatus.ACTIVE) {
      throw new BadRequestException("Creative media must be ACTIVE");
    }
    if (media.getPurpose() != MediaPurpose.AD_CREATIVE) {
      throw new BadRequestException("Creative media must use AD_CREATIVE purpose");
    }
    if (role == CreativeAssetRole.VIDEO && media.getMediaType() != MediaType.VIDEO) {
      throw new BadRequestException("VIDEO creative assets must reference video media");
    }
    if ((role == CreativeAssetRole.IMAGE || role == CreativeAssetRole.LOGO)
        && media.getMediaType() != MediaType.IMAGE) {
      throw new BadRequestException("IMAGE and LOGO creative assets must reference image media");
    }
  }

  private void apply(
      CreativePack pack,
      String name,
      String businessName,
      String offerText,
      String primaryMessage,
      String callToAction,
      String destinationUrl,
      List<String> headlines,
      List<String> descriptions) {
    pack.setName(name.trim());
    pack.setBusinessName(normalizeText(businessName));
    pack.setOfferText(normalizeText(offerText));
    pack.setPrimaryMessage(normalizeText(primaryMessage));
    pack.setCallToAction(normalizeText(callToAction));
    pack.setDestinationUrl(normalizeText(destinationUrl));
    pack.setHeadlinesJson(jsonNullable(headlines));
    pack.setDescriptionsJson(jsonNullable(descriptions));
  }

  private void validateChannel(
      Long companyId, Long campaignId, Long channelId, com.messaging.ads.enums.AdProvider provider) {
    if (channelId == null) return;
    CampaignChannel channel =
        channelRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign channel not found"));
    if (channel.getProvider() != provider) {
      throw new BadRequestException("Creative variant provider does not match campaign channel");
    }
  }

  private ReachlyoCampaign requireCampaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private CreativePack requirePack(Long companyId, Long campaignId, Long packId) {
    return packRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(packId, companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Creative pack not found"));
  }

  private void requireEditable(ReachlyoCampaign campaign) {
    if (campaign.getStatus() != ReachlyoCampaignStatus.DRAFT
        && campaign.getStatus() != ReachlyoCampaignStatus.READY) {
      throw new ConflictException("Creative definition can only be edited while campaign is DRAFT or READY");
    }
  }

  private CreativePackResponse response(Long companyId, CreativePack pack) {
    return CreativePackResponse.from(
        pack,
        assetRepository.findByCompanyIdAndCreativePackIdOrderBySortOrderAscIdAsc(companyId, pack.getId()),
        variantRepository.findByCompanyIdAndCreativePackIdOrderByCreatedAtAsc(companyId, pack.getId()));
  }

  private String normalizeText(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String jsonNullable(Object value) {
    return value == null ? null : json(value);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Unable to serialize creative configuration");
    }
  }

  private String normalizeJson(String value) {
    String normalized = normalizeText(value);
    if (normalized == null) return null;
    try {
      return objectMapper.writeValueAsString(objectMapper.readTree(normalized));
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Creative asset specification must be valid JSON");
    }
  }
}
