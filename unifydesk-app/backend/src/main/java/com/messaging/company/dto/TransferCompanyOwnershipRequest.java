package com.messaging.company.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferCompanyOwnershipRequest(@NotBlank String targetUserId) {}
