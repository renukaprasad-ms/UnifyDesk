package com.messaging.storage.dto;

import com.messaging.storage.enums.StorageUsageType;

public record StorageUsageBreakdownResponse(
    StorageUsageType type, long usedBytes, long reservedBytes) {}
