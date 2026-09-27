package com.messaging.managedsms.metering;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({
  "/api/v1/companies/{companyId}/managed-sms",
  "/api/companies/{companyId}/managed-sms"
})
@RequiredArgsConstructor
public class ManagedSmsUsageController {

  private final ManagedSmsUsageService usageService;
  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/usage")
  public ResponseEntity<ApiResponse<ManagedSmsUsageService.UsageSummary>> usage(
      @PathVariable Long companyId) {
    authorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.BROADCAST_READ);
    boolean enabled =
        properties.providerConfigured() && runtimeRepository.tenantSendingEnabled(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, usageService.summary(companyId, enabled), "Managed SMS usage fetched"));
  }
}
