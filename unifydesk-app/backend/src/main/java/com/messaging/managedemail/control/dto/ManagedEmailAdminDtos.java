package com.messaging.managedemail.control.dto;

import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ManagedEmailAdminDtos {

  private ManagedEmailAdminDtos() {}

  public record ActionRequest(@NotBlank @Size(max = 500) String reason) {}

  public record ContainmentResponse(
      String companyId,
      TenantStatus status,
      SendingStatus sendingStatus,
      boolean connectionSendingEnabled,
      Instant changedAt) {}

  public enum DomainDecisionAction {
    APPROVE,
    ASSIGN_ALTERNATIVE,
    REJECT
  }

  public record DomainDecisionRequest(
      @NotNull DomainDecisionAction action,
      @Size(min = 2, max = 63) String alternativeSubdomain,
      @Size(max = 500) String reason) {}

  public record HostedDomainReviewItem(
      String domainId,
      Long companyId,
      String companyName,
      String requestedSubdomain,
      String displayDomain,
      com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus status,
      Instant requestedAt,
      Long requestedByUserId,
      String userEmail,
      String rejectionReason,
      boolean adminAssignedAlternative) {}
}
