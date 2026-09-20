package com.messaging.channel.microsoft.dto;

import java.time.Instant;
import java.util.List;

public record MicrosoftSendResponse(
    String connectionId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant acceptedAt) {}
