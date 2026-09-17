package com.messaging.company.dto;

import java.util.Set;

public record CompanyIamPermissionsResponse(
    Long companyId,
    boolean owner,
    Set<String> permissions) {}
