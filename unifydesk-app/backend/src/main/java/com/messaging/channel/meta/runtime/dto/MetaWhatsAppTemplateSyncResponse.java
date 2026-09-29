package com.messaging.channel.meta.runtime.dto;

import java.time.Instant;

public record MetaWhatsAppTemplateSyncResponse(
    int synced, int approved, int rejected, Instant syncedAt) {}
