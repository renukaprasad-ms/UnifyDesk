package com.messaging.media.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.enums.MediaType;
import com.messaging.media.enums.StorageProviderType;
import java.time.Instant;

public record MediaResponse(
    String mediaId,
    String ownerUserId,
    StorageProviderType storageProvider,
    String bucket,
    String objectKey,
    String originalFileName,
    String contentType,
    long sizeBytes,
    MediaType mediaType,
    MediaPurpose purpose,
    MediaStatus status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant deletedAt) {}
