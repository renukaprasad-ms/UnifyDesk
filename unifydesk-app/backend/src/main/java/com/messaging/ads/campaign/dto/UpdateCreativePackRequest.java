package com.messaging.ads.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateCreativePackRequest(
    @Size(min = 1, max = 255) String name,
    @Size(max = 255) String businessName,
    String offerText,
    String primaryMessage,
    @Size(max = 80) String callToAction,
    @Size(max = 2048) String destinationUrl,
    List<@Size(max = 500) String> headlines,
    List<@Size(max = 1000) String> descriptions,
    List<@Valid CreativePackAssetInput> assets) {}
