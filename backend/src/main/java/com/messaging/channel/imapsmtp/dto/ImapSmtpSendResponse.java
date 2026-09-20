package com.messaging.channel.imapsmtp.dto;

import java.time.Instant;
import java.util.List;

public record ImapSmtpSendResponse(
    String connectionId,
    String messageId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant sentAt) {}
