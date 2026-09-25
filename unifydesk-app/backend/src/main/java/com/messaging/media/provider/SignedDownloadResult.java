package com.messaging.media.provider;

import java.time.Instant;

public record SignedDownloadResult(String url, Instant expiresAt) {}
