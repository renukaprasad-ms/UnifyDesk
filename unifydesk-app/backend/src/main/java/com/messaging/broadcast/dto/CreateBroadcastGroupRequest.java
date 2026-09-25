package com.messaging.broadcast.dto;

import com.messaging.channel.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBroadcastGroupRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 500) String description,
    @NotNull ChannelType channelType) {}
