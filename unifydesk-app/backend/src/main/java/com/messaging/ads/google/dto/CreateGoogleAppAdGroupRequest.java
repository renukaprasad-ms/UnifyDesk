package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateGoogleAppAdGroupRequest(
    @NotBlank @Size(max = 255) String name,
    @Positive Long targetCpaMinor,
    @Size(max = 255) String userListId) {}
