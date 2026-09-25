package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleUrlOptionsRequest(
    @Size(max = 2048) String trackingUrlTemplate,
    @Size(max = 2048) String finalUrlSuffix,
    @Size(max = 8) List<@Valid GoogleUrlCustomParameter> customParameters) {}
