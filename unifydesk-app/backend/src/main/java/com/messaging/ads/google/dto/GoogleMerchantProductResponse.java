package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleMerchantProductResponse(
    String name,
    String offerId,
    String contentLanguage,
    String feedLabel,
    String dataSource,
    String title,
    String description,
    String link,
    String imageLink,
    String availability,
    String condition,
    String brand,
    String priceAmountMicros,
    String priceCurrency,
    List<String> gtins,
    String googleProductCategory,
    List<String> issues) {}
