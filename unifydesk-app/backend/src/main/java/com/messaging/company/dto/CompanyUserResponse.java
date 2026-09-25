package com.messaging.company.dto;

import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import java.time.Instant;
import java.util.List;

public record CompanyUserResponse(
    String memberId,
    String userId,
    String name,
    String username,
    String email,
    boolean verified,
    Boolean twoFactorEnabled,
    CompanyRole role,
    CompanyMemberStatus status,
    Instant joinedAt,
    List<String> customRoles) {}
