package com.messaging.ads.google.dto;

public record GoogleExperimentActionResponse(
    String experimentId,
    String action,
    String operationName,
    GoogleExperimentResponse experiment) {}
