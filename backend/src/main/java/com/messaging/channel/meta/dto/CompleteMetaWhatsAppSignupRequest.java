package com.messaging.channel.meta.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteMetaWhatsAppSignupRequest(
    @NotBlank String state,
    @NotBlank String code,
    @NotBlank String wabaId,
    @NotBlank String phoneNumberId) {}
