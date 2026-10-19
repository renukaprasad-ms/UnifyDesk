package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.billing.mailbridge.MailProjectBillingBridgeService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit Super Admin verification required. External Mail project identifiers are never inferred
 * from or conflated with platform company identifiers.
 */
@RestController
@RequestMapping("/api/v1/admin/billing/mail-project-links")
@RequiredArgsConstructor
public class BillingMailProjectLinkController {
  private final MailProjectBillingBridgeService bridge;
  private final PlatformAdminAuthorizationService authorization;
  private final CurrentUserService currentUserService;

  public record CreateLinkRequest(
      @NotBlank String companyId,
      @NotBlank String mailProjectId,
      @NotBlank @Size(min = 12, max = 500) String ownershipProof,
      @AssertTrue boolean ownershipVerified) {}

  private Long id(String raw, String kind) {
    try {
      long parsed = Long.parseLong(raw);
      if (parsed > 0) return parsed;
    } catch (NumberFormatException ignored) {
      // Return a uniform bad-request response without exposing internal numeric IDs.
    }
    throw new BadRequestException("Invalid " + kind + " identifier");
  }

  private void authorize() {
    authorization.requireSuperAdmin(currentUserService.currentUserId());
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<MailProjectBillingBridgeService.Link>>> list() {
    authorize();
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), bridge.links(), "Mail project links fetched"));
  }

  @GetMapping("/usage")
  public ResponseEntity<ApiResponse<List<MailProjectBillingBridgeService.MailUsageTotals>>> usage(
      @RequestParam Instant from, @RequestParam Instant to) {
    authorize();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            bridge.usage(from, to),
            "Imported Mail project handoff totals fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MailProjectBillingBridgeService.Link>> link(
      @Valid @RequestBody CreateLinkRequest request) {
    authorize();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                bridge.link(
                    id(request.companyId(), "company"),
                    id(request.mailProjectId(), "Mail project"),
                    currentUserService.currentUserId(),
                    request.ownershipProof(),
                    request.ownershipVerified()),
                "Mail project linked for reporting; no platform charges created"));
  }

  @PostMapping("/{projectId}/sync")
  public ResponseEntity<ApiResponse<MailProjectBillingBridgeService.SyncResult>> sync(
      @PathVariable String projectId) {
    authorize();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            bridge.sync(id(projectId, "Mail project")),
            "Mail handoff usage synchronized"));
  }

  public record MailReplayRequest(
      @jakarta.validation.constraints.NotNull Instant from,
      @jakarta.validation.constraints.NotBlank String afterId) {}

  @PostMapping("/{projectId}/replay")
  public ResponseEntity<ApiResponse<MailProjectBillingBridgeService.SyncResult>> replay(
      @PathVariable String projectId, @Valid @RequestBody MailReplayRequest request) {
    authorize();
    long cursor;
    try {
      cursor = Long.parseLong(request.afterId());
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid Mail replay cursor");
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            bridge.replay(id(projectId, "Mail project"), request.from(), cursor),
            "Mail handoff replay completed without financial charges"));
  }

  @PostMapping("/{projectId}/pause")
  public ResponseEntity<ApiResponse<MailProjectBillingBridgeService.Link>> pause(
      @PathVariable String projectId) {
    authorize();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            bridge.setState(id(projectId, "Mail project"), "PAUSED"),
            "Mail usage sync paused"));
  }

  @PostMapping("/{projectId}/resume")
  public ResponseEntity<ApiResponse<MailProjectBillingBridgeService.Link>> resume(
      @PathVariable String projectId) {
    authorize();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            bridge.setState(id(projectId, "Mail project"), "ACTIVE"),
            "Mail usage sync resumed"));
  }
}
