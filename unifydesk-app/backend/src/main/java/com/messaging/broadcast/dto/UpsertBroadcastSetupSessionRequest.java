package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSetupStatus;
import com.messaging.channel.enums.ChannelType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertBroadcastSetupSessionRequest(
    @NotNull ChannelType channelType,
    @NotNull BroadcastSenderProvider provider,
    @Min(0) @Max(20) int currentStep,
    @NotNull BroadcastSetupStatus status,
    @Size(max = 4000) String setupDataJson) {}
