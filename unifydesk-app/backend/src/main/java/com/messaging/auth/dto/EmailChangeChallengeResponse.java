package com.messaging.auth.dto;

public record EmailChangeChallengeResponse(String challengeId, long expiresInSeconds) {}
