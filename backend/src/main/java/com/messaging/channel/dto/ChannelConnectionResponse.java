package com.messaging.channel.dto;

import com.messaging.channel.enums.ChannelConnectionCapability;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.time.Instant;
import java.util.Set;

public record ChannelConnectionResponse(
    String id,
    String companyId,
    ChannelType channelType,
    ProviderType providerType,
    String name,
    String externalAccountId,
    ChannelConnectionStatus status,
    String createdByUserId,
    Instant lastSyncAt,
    Instant disconnectedAt,
    Instant purgeAfter,
    Instant createdAt,
    Instant updatedAt,
    boolean providerConfigured,
    Set<ChannelConnectionCapability> capabilities) {}
