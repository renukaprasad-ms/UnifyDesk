package com.messaging.auth.dto;

public record AuthUserResponse(
    String userId,
    String username,
    String email,
    boolean verified,
    boolean hasCompanies,
    long companyCount) {}
