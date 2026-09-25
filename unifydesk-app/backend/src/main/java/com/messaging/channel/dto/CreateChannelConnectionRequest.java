package com.messaging.channel.dto;

import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChannelConnectionRequest(
    @NotNull ChannelType channelType,
    @NotNull ProviderType providerType,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 255) String externalAccountId) {}
