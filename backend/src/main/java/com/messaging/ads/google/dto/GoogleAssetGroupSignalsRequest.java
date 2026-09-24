package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGroupSignalsRequest(
    List<String> searchThemes,
    List<Long> audienceIds) {}
