package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleDataManagerUploadResponse(
    List<String> requestIds,
    int submittedCount,
    boolean validateOnly,
    List<String> warnings) {}
