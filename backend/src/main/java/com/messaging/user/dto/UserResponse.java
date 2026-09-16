package com.messaging.user.dto;

public record UserResponse(
    Long id,
    String email,
    String name,
    boolean verified,
    String username,
    Boolean twoFactorEnabled) {}
