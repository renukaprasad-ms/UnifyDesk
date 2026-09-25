package com.messaging.storage.dto;

import java.util.List;

public record StorageUsageResponse(
    String companyId,
    long usedBytes,
    long reservedBytes,
    long limitBytes,
    long remainingBytes,
    double usedPercent,
    List<StorageUsageBreakdownResponse> breakdown) {}
