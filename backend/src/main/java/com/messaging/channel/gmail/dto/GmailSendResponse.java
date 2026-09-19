package com.messaging.channel.gmail.dto;

import java.time.Instant;
import java.util.List;

public record GmailSendResponse(
    String connectionId,
    String messageId,
    String threadId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant sentAt) {}
