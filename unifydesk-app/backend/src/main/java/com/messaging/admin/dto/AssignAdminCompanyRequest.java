package com.messaging.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignAdminCompanyRequest(@NotBlank String adminUserId, @NotBlank String companyId) {}
