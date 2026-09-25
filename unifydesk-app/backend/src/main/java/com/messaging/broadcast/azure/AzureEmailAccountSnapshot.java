package com.messaging.broadcast.azure;

public record AzureEmailAccountSnapshot(
    boolean sendingEnabled, Double maxSendRate, String verifiedIdentity) {}
