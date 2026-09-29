package com.messaging.channel.meta.dto;

import com.messaging.channel.enums.DisconnectDataPolicy;

public record MetaWhatsAppDisconnectRequest(DisconnectDataPolicy dataPolicy) {}
