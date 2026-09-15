package com.messaging.company.dto;

import com.messaging.company.enums.CompanyPermission;
import java.util.Set;

public record AccessProfileResponse(
    String profileId, String companyId, String name, String description, Set<CompanyPermission> permissions) {}
