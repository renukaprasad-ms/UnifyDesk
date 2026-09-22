package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBroadcastGroupRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 500) String description) {}
