package com.messaging.managedemail.control.dto;

import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import jakarta.validation.constraints.NotBlank;
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
}
