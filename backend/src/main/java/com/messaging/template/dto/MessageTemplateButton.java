package com.messaging.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MessageTemplateButton(
    @NotBlank @Pattern(regexp = "QUICK_REPLY|URL|PHONE_NUMBER") String type,
    @NotBlank @Size(max = 25) String text,
    @Size(max = 2048) String url,
    @Size(max = 32) String phoneNumber) {}
