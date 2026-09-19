package com.messaging.channel.gmail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GmailSendRequest(
    @Size(max = 100) List<@Email @Size(max = 320) String> to,
    @Size(max = 100) List<@Email @Size(max = 320) String> cc,
    @Size(max = 100) List<@Email @Size(max = 320) String> bcc,
    @Size(max = 998) String subject,
    @NotBlank @Size(max = 100000) String bodyText,
    @Size(max = 255) String threadId,
    @Size(max = 998) String inReplyTo,
    @Size(max = 10000) String references) {}
