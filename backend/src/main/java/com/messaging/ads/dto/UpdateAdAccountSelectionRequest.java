package com.messaging.ads.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAdAccountSelectionRequest(
    @NotNull Boolean managed,
    Boolean primaryAccount) {}
