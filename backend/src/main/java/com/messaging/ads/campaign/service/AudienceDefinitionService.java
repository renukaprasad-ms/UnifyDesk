package com.messaging.ads.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.AudienceDefinitionResponse;
import com.messaging.ads.campaign.dto.CreateAudienceDefinitionRequest;
import com.messaging.ads.campaign.dto.UpdateAudienceDefinitionRequest;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.AudienceMapping;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.AudienceMappingRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudienceDefinitionService {

  private final ReachlyoCampaignRepository campaignRepository;
  private final AudienceDefinitionRepository definitionRepository;
  private final AudienceMappingRepository mappingRepository;
  private final CampaignChannelRepository channelRepository;
  private final AdAudienceRepository adAudienceRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<AudienceDefinitionResponse> list(Long companyId, Long campaignId) {
    requireCampaign(companyId, campaignId);
    return definitionRepository
        .findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtAsc(companyId, campaignId)
        .stream()
        .map(definition -> response(companyId, definition))
        .toList();
  }

  @Transactional(readOnly = true)
  public AudienceDefinitionResponse get(Long companyId, Long campaignId, Long definitionId) {
    requireCampaign(companyId, campaignId);
    return response(companyId, requireDefinition(companyId, campaignId, definitionId));
  }

  @Transactional
  public AudienceDefinitionResponse create(
      Long companyId, Long userId, Long campaignId, CreateAudienceDefinitionRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    validateAge(request.ageMin(), request.ageMax());

    AudienceDefinition definition = new AudienceDefinition();
    definition.setCompanyId(companyId);
    definition.setReachlyoCampaignId(campaignId);
    definition.setCreatedByUserId(userId);
    definition.setName(request.name().trim());
    definition.setDescription(normalizeText(request.description()));
    definition.setAgeMin(request.ageMin());
    definition.setAgeMax(request.ageMax());
    definition.setLocationsJson(jsonNullable(request.locations()));
    definition.setLanguagesJson(jsonNullable(request.languages()));
    definition.setGendersJson(jsonNullable(request.genders()));
    definition.setCustomerSegmentsJson(jsonNullable(request.customerSegmentIds()));
    definition.setRetargetingJson(jsonNullable(request.retargeting()));
    definition.setExclusionsJson(jsonNullable(request.exclusions()));
    definition.setInterestsJson(jsonNullable(request.interests()));
    definition.setSearchIntentJson(jsonNullable(request.searchIntents()));
    definitionRepository.save(definition);
    return response(companyId, definition);
  }

  @Transactional
  public AudienceDefinitionResponse update(
      Long companyId,
      Long campaignId,
      Long definitionId,
      UpdateAudienceDefinitionRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    AudienceDefinition definition = requireDefinition(companyId, campaignId, definitionId);

    Integer ageMin = request.ageMin() == null ? definition.getAgeMin() : request.ageMin();
    Integer ageMax = request.ageMax() == null ? definition.getAgeMax() : request.ageMax();
    validateAge(ageMin, ageMax);

    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new BadRequestException("Audience name cannot be blank");
      }
      definition.setName(request.name().trim());
    }
    if (request.description() != null) {
      definition.setDescription(normalizeText(request.description()));
    }
    if (request.ageMin() != null) definition.setAgeMin(request.ageMin());
    if (request.ageMax() != null) definition.setAgeMax(request.ageMax());
    if (request.locations() != null) definition.setLocationsJson(json(request.locations()));
    if (request.languages() != null) definition.setLanguagesJson(json(request.languages()));
    if (request.genders() != null) definition.setGendersJson(json(request.genders()));
    if (request.customerSegmentIds() != null) {
      definition.setCustomerSegmentsJson(json(request.customerSegmentIds()));
    }
    if (request.retargeting() != null) definition.setRetargetingJson(json(request.retargeting()));
    if (request.exclusions() != null) definition.setExclusionsJson(json(request.exclusions()));
    if (request.interests() != null) definition.setInterestsJson(json(request.interests()));
    if (request.searchIntents() != null) {
      definition.setSearchIntentJson(json(request.searchIntents()));
    }
    definitionRepository.save(definition);
    return response(companyId, definition);
  }

  @Transactional
  public void delete(Long companyId, Long campaignId, Long definitionId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    AudienceDefinition definition = requireDefinition(companyId, campaignId, definitionId);
    mappingRepository.deleteByCompanyIdAndAudienceDefinitionId(companyId, definitionId);
    definitionRepository.delete(definition);
  }

  @Transactional
  public AudienceMapping upsertMapping(
      Long companyId,
      Long campaignId,
      Long definitionId,
      Long channelId,
      AudienceMappingStatus mappingStatus,
      Long localAudienceId,
      String externalAudienceId,
      Object providerCriteria,
      String notes) {
    requireCampaign(companyId, campaignId);
    requireDefinition(companyId, campaignId, definitionId);
    CampaignChannel channel =
        channelRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign channel not found"));

    AdAudience localAudience = null;
    if (localAudienceId != null) {
      localAudience =
          adAudienceRepository
              .findByIdAndCompanyId(localAudienceId, companyId)
              .orElseThrow(() -> new NotFoundException("Provider audience not found"));
      if (localAudience.getProvider() != channel.getProvider()) {
        throw new BadRequestException("Provider audience does not match campaign channel");
      }
    }

    AudienceMapping mapping =
        mappingRepository
            .findByCompanyIdAndAudienceDefinitionIdAndCampaignChannelId(
                companyId, definitionId, channelId)
            .orElseGet(AudienceMapping::new);
    if (mapping.getId() == null) {
      mapping.setCompanyId(companyId);
      mapping.setReachlyoCampaignId(campaignId);
      mapping.setAudienceDefinitionId(definitionId);
      mapping.setCampaignChannelId(channelId);
    }
    mapping.setProvider(channel.getProvider());
    mapping.setMappingStatus(mappingStatus);
    mapping.setLocalAudienceId(localAudienceId);
    mapping.setExternalAudienceId(
        normalizeText(
            externalAudienceId != null
                ? externalAudienceId
                : localAudience == null ? null : localAudience.getExternalAudienceId()));
    mapping.setProviderCriteriaJson(jsonNullable(providerCriteria));
    mapping.setNotes(normalizeText(notes));
    return mappingRepository.save(mapping);
  }

  private ReachlyoCampaign requireCampaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private AudienceDefinition requireDefinition(Long companyId, Long campaignId, Long definitionId) {
    return definitionRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(definitionId, companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Audience definition not found"));
  }

  private void requireEditable(ReachlyoCampaign campaign) {
    if (campaign.getStatus() != ReachlyoCampaignStatus.DRAFT
        && campaign.getStatus() != ReachlyoCampaignStatus.READY) {
      throw new ConflictException(
          "Audience definition can only be edited while campaign is DRAFT or READY");
    }
  }

  private void validateAge(Integer min, Integer max) {
    if (min != null && max != null && max < min) {
      throw new BadRequestException(
          "Audience maximum age must be greater than or equal to minimum age");
    }
  }

  private AudienceDefinitionResponse response(Long companyId, AudienceDefinition definition) {
    return AudienceDefinitionResponse.from(
        definition,
        mappingRepository.findByCompanyIdAndAudienceDefinitionIdOrderByIdAsc(
            companyId, definition.getId()));
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
      throw new BadRequestException("Unable to serialize audience definition");
    }
  }
}
