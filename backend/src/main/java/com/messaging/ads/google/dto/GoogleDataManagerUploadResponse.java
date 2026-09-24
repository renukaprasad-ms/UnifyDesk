package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleDataManagerUploadResponse(
    String requestId,
    int submittedCount,
    boolean validateOnly,
    List<String> warnings) {}
