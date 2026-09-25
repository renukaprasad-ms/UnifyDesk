package com.messaging.ads.google.dto;

import com.messaging.ads.campaign.dto.AudienceLocationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleKeywordPlannerHistoricalRequest(
    @NotEmpty @Size(max = 10000) List<@Size(min = 1, max = 80) String> keywords,
    @Size(max = 10) List<@Valid AudienceLocationInput> locations,
    @Size(max = 64) String language,
    @Size(max = 32) String network,
    Boolean includeAdultKeywords) {}
