package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.AudienceMapping;
import java.time.Instant;
import java.util.List;

public record AudienceDefinitionResponse(
    String id,
    String campaignId,
    String name,
    String description,
    Integer ageMin,
    Integer ageMax,
    String locationsJson,
    String languagesJson,
    String gendersJson,
    String customerSegmentsJson,
    String retargetingJson,
    String exclusionsJson,
    String interestsJson,
    String searchIntentJson,
    String createdByUserId,
    List<AudienceMappingResponse> mappings,
    Instant createdAt,
    Instant updatedAt) {
  public static AudienceDefinitionResponse from(
      AudienceDefinition definition, List<AudienceMapping> mappings) {
    return new AudienceDefinitionResponse(
        definition.getId().toString(),
        definition.getReachlyoCampaignId().toString(),
        definition.getName(),
        definition.getDescription(),
        definition.getAgeMin(),
        definition.getAgeMax(),
        definition.getLocationsJson(),
        definition.getLanguagesJson(),
        definition.getGendersJson(),
        definition.getCustomerSegmentsJson(),
        definition.getRetargetingJson(),
        definition.getExclusionsJson(),
        definition.getInterestsJson(),
        definition.getSearchIntentJson(),
        definition.getCreatedByUserId().toString(),
        mappings.stream().map(AudienceMappingResponse::from).toList(),
        definition.getCreatedAt(),
        definition.getUpdatedAt());
  }
}
