package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/email-product/organizations/{organizationId}/invitations")
public class ConsoleOperatorInvitationsController {
  private final ConsoleAuthService service;

  public ConsoleOperatorInvitationsController(ConsoleAuthService service) {
    this.service = service;
  }

  public record NewInvitation(
      @NotBlank @Email String email,
      @NotBlank @Size(max = 120) String displayName,
      @NotNull ConsoleRole role) {}

  @PostMapping
  public ResponseEntity<ApiResponse<ConsoleAuthService.Invite>> invite(
      @PathVariable @Positive Long organizationId, @Valid @RequestBody NewInvitation request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.invite(
                    organizationId, request.email(), request.displayName(), request.role()),
                "Invitation created; share secret securely"));
  }
}
