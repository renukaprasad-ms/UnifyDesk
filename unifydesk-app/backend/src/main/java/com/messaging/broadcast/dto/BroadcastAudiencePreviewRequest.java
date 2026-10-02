package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.channel.enums.ChannelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BroadcastAudiencePreviewRequest(
    @NotNull BroadcastAudienceSourceType sourceType,
    Long sourceId,
    @Size(max = 500) List<Long> contactIds,
    @NotNull ChannelType channelType,
    @NotNull Long senderId) {}
