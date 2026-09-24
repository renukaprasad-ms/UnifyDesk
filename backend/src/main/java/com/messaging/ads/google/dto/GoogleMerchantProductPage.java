package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleMerchantProductPage(
    List<GoogleMerchantProductResponse> products,
    String nextPageToken) {}
