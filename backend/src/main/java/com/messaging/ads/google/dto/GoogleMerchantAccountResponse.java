package com.messaging.ads.google.dto;

public record GoogleMerchantAccountResponse(
    String name,
    String accountId,
    String accountName,
    String timeZone,
    String languageCode,
    boolean adultContent) {}
