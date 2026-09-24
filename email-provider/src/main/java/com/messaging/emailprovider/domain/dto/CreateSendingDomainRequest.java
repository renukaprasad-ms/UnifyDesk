package com.messaging.emailprovider.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSendingDomainRequest(
    @NotBlank(message = "domain is required")
        @Size(max = 253, message = "domain must not exceed 253 characters")
        String domain) {}
