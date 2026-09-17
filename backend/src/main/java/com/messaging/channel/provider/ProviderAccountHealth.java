package com.messaging.channel.provider;

import java.time.Instant;

public record ProviderAccountHealth(
    boolean healthy,
    String status,
    String detail,
    Instant checkedAt) {}
