package com.messaging.emailprovider.mail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SendEmailRequest(
    @NotNull @Positive Long sendingDomainId,
    @NotBlank @Email @Size(max = 320) String from,
    @Email @Size(max = 320) String replyTo,
    List<@Email @Size(max = 320) String> to,
    List<@Email @Size(max = 320) String> cc,
    List<@Email @Size(max = 320) String> bcc,
    @NotBlank @Size(max = 998) String subject,
    String text,
    String html) {}
