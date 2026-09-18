package com.messaging.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record CreateCompanyInvitationRequest(
    @NotBlank @Email String email, Set<String> roleIds) {}
