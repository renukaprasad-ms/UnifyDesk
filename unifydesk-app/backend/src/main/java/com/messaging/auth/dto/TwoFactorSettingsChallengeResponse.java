package com.messaging.auth.dto;

public record TwoFactorSettingsChallengeResponse(String challengeToken, long expiresInSeconds) {}
