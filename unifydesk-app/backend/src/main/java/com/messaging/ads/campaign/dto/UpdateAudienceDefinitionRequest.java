package com.messaging.ads.campaign.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateAudienceDefinitionRequest(
    @Size(min = 1, max = 255) String name,
    String description,
    @Min(13) @Max(100) Integer ageMin,
    @Min(13) @Max(100) Integer ageMax,
    List<@Valid AudienceLocationInput> locations,
    List<@Size(max = 40) String> languages,
    List<@Size(max = 40) String> genders,
    List<@Size(max = 255) String> customerSegmentIds,
    JsonNode retargeting,
    JsonNode exclusions,
    List<@Size(max = 255) String> interests,
    List<@Size(max = 255) String> searchIntents) {}
