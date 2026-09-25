package com.messaging.channel.imapsmtp.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.imapsmtp.MailSecurityMode;

public record ImapSmtpConnectionResponse(
    String connectionId,
    String email,
    String name,
    ChannelConnectionStatus status,
    String imapHost,
    int imapPort,
    MailSecurityMode imapSecurity,
    String smtpHost,
    int smtpPort,
    MailSecurityMode smtpSecurity,
    String sentFolder) {}
