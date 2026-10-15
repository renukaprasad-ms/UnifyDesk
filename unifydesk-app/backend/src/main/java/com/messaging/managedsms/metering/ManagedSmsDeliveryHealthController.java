package com.messaging.managedsms.metering;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.managedsms.metering.ManagedSmsDeliveryHealthService.DeliveryHealth;
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
public class ManagedSmsDeliveryHealthController {
  private final ManagedSmsDeliveryHealthService healthService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/delivery-health")
  public ResponseEntity<ApiResponse<DeliveryHealth>> health(@PathVariable Long companyId) {
    authorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, healthService.get(companyId), "Managed SMS delivery health fetched"));
  }
}
