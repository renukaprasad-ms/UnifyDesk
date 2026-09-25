package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleBrandSuggestionResponse(
    String id,
    String name,
    List<String> urls,
    String state) {}
