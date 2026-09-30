package com.messaging.channel.meta.runtime.dto;

import java.time.Instant;
import java.util.List;

public record MetaWhatsAppComposerResponse(
    boolean freeFormAllowed,
    Instant customerServiceExpiresAt,
    boolean templateRequired,
    List<String> supportedMedia,
    String connectionStatus) {}
