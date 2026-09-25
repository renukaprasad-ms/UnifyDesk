package com.messaging.emailtemplate.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateEmailTemplateAssetRequest(@NotBlank String mediaId) {}
