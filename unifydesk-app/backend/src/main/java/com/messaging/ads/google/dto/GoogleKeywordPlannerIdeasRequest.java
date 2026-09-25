package com.messaging.ads.google.dto;

import com.messaging.ads.campaign.dto.AudienceLocationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleKeywordPlannerIdeasRequest(
    @Size(max = 20) List<@Size(min = 1, max = 80) String> seedKeywords,
    @Size(max = 2048) String url,
    Boolean useEntireSite,
    @Size(max = 10) List<@Valid AudienceLocationInput> locations,
    @Size(max = 64) String language,
    @Size(max = 32) String network,
    Boolean includeAdultKeywords,
    @Min(1) @Max(1000) Integer pageSize,
    @Size(max = 4096) String pageToken) {}
