package com.messaging.auth.dto;

public record PasswordResetChallengeResponse(String challengeId, long expiresInSeconds) {}
