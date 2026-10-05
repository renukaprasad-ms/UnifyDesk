package com.messaging.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImmediateCancellationRequest(@NotBlank @Size(max = 1000) String reason) {}
