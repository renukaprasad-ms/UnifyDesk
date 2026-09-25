package com.messaging.media.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record MediaAccessResponse(
    String url, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt) {}
