package com.messaging.company.dto;

import java.time.Instant;

public record GrantAccessProfileRequest(Instant expiresAt) {}
