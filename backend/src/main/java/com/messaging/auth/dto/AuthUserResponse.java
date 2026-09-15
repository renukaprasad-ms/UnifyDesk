package com.messaging.auth.dto;

public record AuthUserResponse(
    String userId,
    String name,
    String username,
    String email,
    String profilePictureMediaId,
    boolean verified,
    boolean hasCompanies,
    long companyCount) {}
