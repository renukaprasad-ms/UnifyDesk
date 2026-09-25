package com.messaging.channel.meta.dto;

public record MetaWhatsAppSignupConfigResponse(
    String appId,
    String configurationId,
    String graphApiVersion,
    String state,
    boolean coexistenceEnabled) {}
