package com.messaging.ads.google.dto;

import com.messaging.ads.campaign.dto.AudienceLocationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleDemandGenTargetingRequest(
    String channelStrategy,
    @Valid GoogleDemandGenSelectedChannels selectedChannels,
    @Valid @Size(max = 50) List<AudienceLocationInput> locations,
    @Size(max = 50) List<@Size(max = 160) String> locationResourceNames,
    @Valid @Size(max = 50) List<AudienceLocationInput> excludedLocations,
    @Size(max = 50) List<@Size(max = 160) String> excludedLocationResourceNames,
    @Size(max = 20) List<@Size(max = 100) String> languages,
    @Size(max = 20) List<@Size(max = 160) String> languageResourceNames,
    @Size(max = 20) List<@Size(max = 160) String> audienceResourceNames) {}
