package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAdGroupStatusRequest(@NotBlank String status) {}
