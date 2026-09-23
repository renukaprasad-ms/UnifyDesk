package com.messaging.ads.meta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CompleteMetaAdsConnectionRequest(
    @NotBlank String sessionId,
    String businessId,
    @NotEmpty List<@NotBlank String> accountIds) {}
