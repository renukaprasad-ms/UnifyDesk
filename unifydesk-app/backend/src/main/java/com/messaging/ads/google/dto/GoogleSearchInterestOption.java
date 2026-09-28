package com.messaging.ads.google.dto;

public record GoogleSearchInterestOption(
    String id,
    String resourceName,
    String name,
    String taxonomyType,
    String parentResourceName,
    boolean launchedToAll,
    boolean searchAvailable) {}
