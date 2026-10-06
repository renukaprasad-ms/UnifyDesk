package com.messaging.company.dto;

import com.messaging.company.enums.CompanyMemberStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCompanyMemberStatusRequest(@NotNull CompanyMemberStatus status) {}
