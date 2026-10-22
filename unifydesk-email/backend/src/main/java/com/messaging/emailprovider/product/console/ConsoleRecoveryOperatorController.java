package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Private service-token provisioning, after the operator independently verifies account ownership.
 */
@Validated
@RestController
public class ConsoleRecoveryOperatorController {
  private final ConsoleSecurityService security;

  public ConsoleRecoveryOperatorController(ConsoleSecurityService security) {
    this.security = security;
  }

  @PostMapping(
      "/internal/v1/email-product/organizations/{organizationId}/users/{userId}/password-reset")
  public ResponseEntity<ApiResponse<ConsoleSecurityService.Reset>> reset(
      @PathVariable @Positive long organizationId, @PathVariable @Positive long userId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                200,
                security.issueReset(organizationId, userId),
                "Deliver this one-time token privately to the verified account owner"));
  }
}
