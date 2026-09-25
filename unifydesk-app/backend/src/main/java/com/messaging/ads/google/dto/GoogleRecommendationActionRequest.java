package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GoogleRecommendationActionRequest(
    @NotEmpty List<String> resourceNames) {}
