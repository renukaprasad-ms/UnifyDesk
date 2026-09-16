package com.messaging.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Email String email,
    @Size(max = 120) String name,
    @Size(min = 3, max = 50) String username,
    Boolean verified) {}
