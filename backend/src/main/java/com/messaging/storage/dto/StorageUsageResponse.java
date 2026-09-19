package com.messaging.storage.dto;

public record StorageUsageResponse(
    String companyId,
    long usedBytes,
    long reservedBytes,
    long limitBytes,
    long remainingBytes,
    double usedPercent) {}
