package com.messaging.company.dto;

import com.messaging.company.enums.CompanyPermission;
import java.util.Set;

public record CompanyPermissionResponse(String companyId, Set<CompanyPermission> permissions) {}
