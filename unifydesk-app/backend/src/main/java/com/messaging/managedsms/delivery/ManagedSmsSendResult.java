package com.messaging.managedsms.delivery;

import java.time.Instant;

public record ManagedSmsSendResult(String providerRequestId, Instant acceptedAt) {}
