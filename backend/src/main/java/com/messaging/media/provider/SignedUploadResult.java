package com.messaging.media.provider;

import java.time.Instant;
import java.util.Map;

public record SignedUploadResult(
    String uploadUrl, Instant expiresAt, Map<String, String> requiredHeaders) {}
