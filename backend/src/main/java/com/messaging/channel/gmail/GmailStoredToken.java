package com.messaging.channel.gmail;

public record GmailStoredToken(
    String accessToken, String refreshToken, String tokenType, String scope, String emailAddress) {}
