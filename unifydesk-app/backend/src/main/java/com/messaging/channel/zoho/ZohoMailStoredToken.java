package com.messaging.channel.zoho;

public record ZohoMailStoredToken(
    String accessToken,
    String refreshToken,
    String tokenType,
    String scope,
    String accountsServer,
    String mailBaseUrl,
    String accountId,
    String emailAddress,
    String displayName,
    String inboxFolderId,
    String sentFolderId) {}
