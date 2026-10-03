package com.messaging.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokePlanAssignmentRequest(@NotBlank @Size(max = 500) String reason) {}
