package com.messaging.channel.meta.runtime.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record MetaWhatsAppTemplateResponse(
    String id,
    String providerTemplateId,
    String name,
    String language,
    String category,
    String status,
    JsonNode components,
    Instant syncedAt) {}
