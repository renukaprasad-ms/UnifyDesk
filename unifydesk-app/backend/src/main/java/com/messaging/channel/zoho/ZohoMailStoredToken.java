package com.messaging.channel.zoho;

public record ZohoMailStoredToken(
    String accessToken,
    String refreshToken,
    String tokenType,
    String scope,
    String accountId,
    String emailAddress,
    String displayName,
    String location,
    String accountsServer,
    String mailApiBaseUrl) {}
