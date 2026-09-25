package com.messaging.channel.microsoft;

public record MicrosoftStoredToken(
    String accessToken,
    String refreshToken,
    String tokenType,
    String scope,
    String userId,
    String emailAddress) {}
