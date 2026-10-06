package com.messaging.company.dto;

import com.messaging.company.enums.CompanyRole;
import jakarta.validation.constraints.NotNull;

public record UpdateCompanyMemberRoleRequest(@NotNull CompanyRole role) {}
