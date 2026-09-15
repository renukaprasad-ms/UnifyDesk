package com.messaging.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertEntitlementRequest(
    @NotBlank @Size(max = 120) String key, @NotBlank @Size(max = 512) String value) {}
