package com.messaging.managedemail.metering;

import java.time.LocalDate;

public record ManagedEmailUsageResponse(
    LocalDate periodStart,
    LocalDate periodEndExclusive,
    long accepted,
    long reserved,
    long adjustments,
    long billable,
    long limit,
    long remaining,
    boolean limitReached) {}
