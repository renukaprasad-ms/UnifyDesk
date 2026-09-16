package com.messaging.admin.dto;
import com.messaging.company.enums.CompanyStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateCompanyStatusRequest(@NotNull CompanyStatus status) {}
