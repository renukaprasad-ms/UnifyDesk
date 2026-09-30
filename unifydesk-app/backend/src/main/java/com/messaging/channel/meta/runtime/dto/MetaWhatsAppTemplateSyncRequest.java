package com.messaging.channel.meta.runtime.dto;

import jakarta.validation.constraints.NotBlank;

public record MetaWhatsAppTemplateSyncRequest(@NotBlank String connectionId) {}
