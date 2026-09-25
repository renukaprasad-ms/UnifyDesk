package com.messaging.channel.email;

import java.time.Instant;
import java.util.List;

public record EmailSendResult(
    String connectionId,
    String externalMessageId,
    String externalThreadId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant acceptedAt) {}
