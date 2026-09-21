package com.messaging.channel.imapsmtp;

public record ImapSmtpStoredCredentials(
    String email,
    String displayName,
    String username,
    String password,
    String imapHost,
    int imapPort,
    MailSecurityMode imapSecurity,
    String smtpHost,
    int smtpPort,
    MailSecurityMode smtpSecurity,
    String sentFolder) {}
