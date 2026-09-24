package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GoogleConversionEventUploadRequest(
    @NotEmpty List<@Valid GoogleConversionEvent> events,
    boolean adUserDataConsent,
    boolean adPersonalizationConsent,
    boolean validateOnly) {}
