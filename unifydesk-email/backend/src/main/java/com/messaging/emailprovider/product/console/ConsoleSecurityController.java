package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/console/v1")
public class ConsoleSecurityController {
  private final ConsoleSecurityService security;

  public ConsoleSecurityController(ConsoleSecurityService security) {
    this.security = security;
  }

  public record Password(@NotBlank @Size(max = 128) String password) {}

  public record Code(@NotBlank @Size(max = 80) String code) {}

  public record Proof(@NotBlank @Size(max = 128) String password, @Size(max = 80) String code) {}

  public record Change(
      @NotBlank @Size(max = 128) String password,
      @NotBlank @Size(min = 12, max = 128) String newPassword,
      @Size(max = 80) String code) {}

  public record Reset(
      @NotBlank @Size(max = 100) String token,
      @NotBlank @Size(min = 12, max = 128) String newPassword,
      @Size(max = 80) String code) {}

  private <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(ApiResponse.success(200, data, message));
  }

  @GetMapping("/security")
  public ResponseEntity<ApiResponse<ConsoleSecurityService.Status>> status(HttpServletRequest req) {
    return ok(security.status(ConsoleAuthFilter.require(req)), "Account security");
  }

  @PostMapping("/security/mfa/enroll")
  public ResponseEntity<ApiResponse<ConsoleSecurityService.Enrollment>> enroll(
      HttpServletRequest req, @Valid @RequestBody Password body) {
    return ok(
        security.begin(ConsoleAuthFilter.require(req), body.password()),
        "Authenticator enrollment");
  }

  @PostMapping("/security/mfa/confirm")
  public ResponseEntity<ApiResponse<List<String>>> confirm(
      HttpServletRequest req, @Valid @RequestBody Code body) {
    return ok(
        security.confirm(ConsoleAuthFilter.require(req), body.code()),
        "Save these recovery codes now; they are shown once");
  }

  @PostMapping("/security/mfa/disable")
  public ResponseEntity<ApiResponse<Void>> disable(
      HttpServletRequest req, @Valid @RequestBody Proof body) {
    security.disable(ConsoleAuthFilter.require(req), body.password(), body.code());
    return ok(null, "Authenticator disabled");
  }

  @PostMapping("/security/password")
  public ResponseEntity<ApiResponse<Void>> password(
      HttpServletRequest req, @Valid @RequestBody Change body) {
    security.changePassword(
        ConsoleAuthFilter.require(req), body.password(), body.newPassword(), body.code());
    return ok(null, "Password changed; sign in again");
  }

  @PostMapping("/password-reset")
  public ResponseEntity<ApiResponse<Void>> reset(
      HttpServletRequest req, @Valid @RequestBody Reset body) {
    security.resetPassword(body.token(), body.newPassword(), body.code(), req.getRemoteAddr());
    return ok(null, "Password reset; sign in again");
  }

  @GetMapping("/security/sessions")
  public ResponseEntity<ApiResponse<List<ConsoleSecurityService.Session>>> sessions(
      HttpServletRequest req) {
    return ok(security.sessions(ConsoleAuthFilter.require(req)), "Active sessions");
  }

  @PostMapping("/security/sessions/revoke")
  public ResponseEntity<ApiResponse<Void>> revoke(
      HttpServletRequest req, @Valid @RequestBody Proof body) {
    security.revokeSessions(ConsoleAuthFilter.require(req), body.password(), body.code());
    return ok(null, "All sessions revoked");
  }
}
