package com.messaging.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
    @NotBlank @Size(max = 160) String name,
    @Email @Size(max = 254) String businessEmail,
    @Size(max = 80) String country,
    @Size(max = 80) String timezone,
    String logoMediaId) {}
