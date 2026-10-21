package com.messaging.emailprovider.suppression.dto;

import com.messaging.emailprovider.suppression.SuppressionReason;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AddSuppressionRequest(
    @NotBlank @Email String email, @NotNull SuppressionReason reason, Instant expiresAt) {}
