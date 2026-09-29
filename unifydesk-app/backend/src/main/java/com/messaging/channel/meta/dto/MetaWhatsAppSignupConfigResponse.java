package com.messaging.channel.meta.dto;

import java.time.Instant;

public record MetaWhatsAppSignupConfigResponse(
    String appId,
    String configurationId,
    String graphApiVersion,
    String embeddedSignupVersion,
    String state,
    Instant expiresAt,
    boolean coexistenceAvailable) {}
