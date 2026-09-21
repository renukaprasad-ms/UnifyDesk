package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record BroadcastMemberInput(
    @NotBlank @Size(max = 320) String destination,
    @Size(max = 255) String displayName,
    Map<String, String> metadata) {}
