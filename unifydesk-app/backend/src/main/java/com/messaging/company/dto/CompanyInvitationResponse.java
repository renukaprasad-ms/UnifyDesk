package com.messaging.company.dto;

import com.messaging.company.enums.CompanyInvitationStatus;
import java.time.Instant;
import java.util.List;

public record CompanyInvitationResponse(
    String invitationId,
    String companyId,
    String companyName,
    String email,
    CompanyInvitationStatus status,
    List<InvitationRoleResponse> roles,
    Instant expiresAt,
    Instant createdAt) {

  public record InvitationRoleResponse(String roleId, String name) {}
}
