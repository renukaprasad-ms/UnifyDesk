package com.messaging.channel.imapsmtp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImapSmtpSendRequest(
    @NotNull List<String> to,
    List<String> cc,
    List<String> bcc,
    @Size(max = 998) String subject,
    @NotNull String bodyText,
    String inReplyTo,
    String references) {}
