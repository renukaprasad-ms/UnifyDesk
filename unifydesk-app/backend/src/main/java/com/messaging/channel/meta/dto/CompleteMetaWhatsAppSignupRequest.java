package com.messaging.channel.meta.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteMetaWhatsAppSignupRequest(
    @NotBlank String state,
    @NotBlank String code,
    String wabaId,
    String phoneNumberId) {}
