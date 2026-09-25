package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.Size;

public record UpdateMetaAdRequest(
    @Size(max = 255) String name,
    String creativeId) {}
