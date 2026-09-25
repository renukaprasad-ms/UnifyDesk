package com.messaging.emailprovider.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateValidationJobRequest(
    @NotEmpty @Size(max = 10000)
        List<@NotBlank @Size(max = 1024) String> emails) {}
