package com.messaging.channel.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.time.Instant;

public record ChannelConnectionResponse(
    Long id,
    Long companyId,
    ChannelType channelType,
    ProviderType providerType,
    String name,
    String externalAccountId,
    ChannelConnectionStatus status,
    Long createdByUserId,
    Instant lastSyncAt,
    Instant createdAt,
    Instant updatedAt,
    boolean providerConfigured) {}
