package com.messaging.media.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.Map;

public record InitiateMediaUploadResponse(
    String mediaId,
    String uploadToken,
    String uploadUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
    Map<String, String> requiredHeaders) {}
