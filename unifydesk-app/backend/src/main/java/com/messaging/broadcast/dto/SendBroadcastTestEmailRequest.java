package com.messaging.broadcast.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SendBroadcastTestEmailRequest(
    @NotBlank @Email @Size(max = 320) String recipient,
    @Size(max = 998) String subject,
    @Size(max = 20000) String bodyText,
    Long templateId,
    @Size(max = 50)
        Map<
                @Pattern(regexp = "[A-Za-z0-9_.-]+") @Size(max = 128) String,
                @Size(max = 5000) String>
            variables) {}
