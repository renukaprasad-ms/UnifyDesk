package com.messaging.media.provider;

import java.time.Duration;

public record SignedUploadRequest(
    String bucket, String objectKey, String contentType, long sizeBytes, Duration expiresIn) {}
