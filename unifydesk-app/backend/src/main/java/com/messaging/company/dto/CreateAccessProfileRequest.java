package com.messaging.company.dto;

import com.messaging.company.enums.CompanyPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateAccessProfileRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 500) String description,
    @NotEmpty Set<CompanyPermission> permissions) {}
