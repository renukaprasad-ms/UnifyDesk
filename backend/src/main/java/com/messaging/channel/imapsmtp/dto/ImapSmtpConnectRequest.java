package com.messaging.channel.imapsmtp.dto;

import com.messaging.channel.imapsmtp.MailSecurityMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImapSmtpConnectRequest(
    @Email @NotBlank @Size(max = 320) String email,
    @Size(max = 120) String displayName,
    @NotBlank @Size(max = 320) String username,
    @NotBlank @Size(max = 1024) String password,
    @NotBlank @Size(max = 255) String imapHost,
    @Min(1) @Max(65535) int imapPort,
    @NotNull MailSecurityMode imapSecurity,
    @NotBlank @Size(max = 255) String smtpHost,
    @Min(1) @Max(65535) int smtpPort,
    @NotNull MailSecurityMode smtpSecurity,
    @Size(max = 255) String sentFolder) {}
