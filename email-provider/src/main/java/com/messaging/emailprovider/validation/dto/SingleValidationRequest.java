package com.messaging.emailprovider.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SingleValidationRequest(@NotBlank @Size(max = 1024) String email) {}
