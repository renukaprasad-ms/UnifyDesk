package com.messaging.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChannelConnectionRequest(
    @NotBlank @Size(max = 120) String name) {}
