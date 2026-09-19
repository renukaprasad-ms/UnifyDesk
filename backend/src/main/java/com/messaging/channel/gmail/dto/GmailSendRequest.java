package com.messaging.channel.gmail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GmailSendRequest(
    @NotBlank @Email @Size(max = 320) String to,
    @Size(max = 998) String subject,
    @NotBlank @Size(max = 100000) String bodyText) {}
