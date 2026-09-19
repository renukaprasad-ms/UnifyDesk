package com.messaging.channel.gmail.dto;

import java.time.Instant;

public record GmailSendResponse(
    String connectionId,
    String messageId,
    String threadId,
    String from,
    String to,
    String subject,
    Instant sentAt) {}
