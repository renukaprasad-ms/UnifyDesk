package com.messaging.broadcast.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendBroadcastTestEmailRequest(
    @NotBlank @Email @Size(max = 320) String recipient,
    @Size(max = 998) String subject,
    @Size(max = 20000) String bodyText) {}
