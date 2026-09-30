package com.messaging.contacts.consent;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.ContactAudiencePermissions;
import com.messaging.contacts.consent.ConsentTypes.*;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
public class ConsentController {

  private final ContactPreferenceService preferenceService;
  private final ContactSuppressionService suppressionService;
  private final ContactEligibilityService eligibilityService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/{contactId}/preferences")
  public ResponseEntity<ApiResponse<List<PreferenceSnapshot>>> preferences(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, ContactAudiencePermissions.CONSENT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, preferenceService.list(companyId, contactId), "Preferences fetched"));
  }

  @PutMapping("/{contactId}/preferences/{channel}/{purpose}")
  public ResponseEntity<ApiResponse<PreferenceSnapshot>> setPreference(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @PathVariable Channel channel,
      @PathVariable Purpose purpose,
      @RequestBody PreferenceRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.CONSENT_MODIFY);
    PreferenceSnapshot response =
        preferenceService.setFromUser(
            companyId,
            contactId,
            request.identityId(),
            channel,
            purpose,
            request.state(),
            request.sourceRef(),
            request.reason(),
            request.occurredAt(),
            request.idempotencyKey(),
            userId);
    return ResponseEntity.ok(ApiResponse.success(200, response, "Preference updated"));
  }

  @GetMapping("/{contactId}/suppressions")
  public ResponseEntity<ApiResponse<List<SuppressionSnapshot>>> suppressions(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, ContactAudiencePermissions.CONSENT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, suppressionService.list(companyId, contactId), "Suppressions fetched"));
  }

  @PostMapping("/{contactId}/suppressions")
  public ResponseEntity<ApiResponse<SuppressionSnapshot>> suppress(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @RequestBody SuppressionRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.CONSENT_MODIFY);
    SuppressionSnapshot response =
        suppressionService.addManual(
            companyId,
            contactId,
            request.identityId(),
            request.channel(),
            request.reasonCode(),
            request.sourceRef(),
            request.reason(),
            request.occurredAt(),
            request.idempotencyKey(),
            userId);
    return ResponseEntity.ok(ApiResponse.success(200, response, "Suppression added"));
  }

  @PostMapping("/{contactId}/suppressions/{suppressionId}/lift")
  public ResponseEntity<ApiResponse<SuppressionSnapshot>> lift(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @PathVariable long suppressionId,
      @RequestBody LiftSuppressionRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.CONSENT_MODIFY);
    SuppressionSnapshot existing =
        suppressionService.list(companyId, contactId).stream()
            .filter(item -> item.id() == suppressionId)
            .findFirst()
            .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
    if (!java.util.Objects.equals(existing.contactId(), contactId)) {
      throw new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND");
    }
    SuppressionSnapshot response =
        suppressionService.liftFromUser(
            companyId, suppressionId, request.reason(), request.idempotencyKey(), userId);
    return ResponseEntity.ok(ApiResponse.success(200, response, "Suppression lifted"));
  }

  @GetMapping("/{contactId}/eligibility")
  public ResponseEntity<ApiResponse<EligibilityResult>> eligibility(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @RequestParam Channel channel,
      @RequestParam(defaultValue = "MARKETING") Purpose purpose,
      @RequestParam(required = false) Long identityId) {
    require(companyId, ContactAudiencePermissions.CONSENT_READ);
    EligibilityResult response =
        eligibilityService.evaluate(
            companyId, contactId, identityId, channel, purpose, new EligibilityContext(null, null, null));
    return ResponseEntity.ok(ApiResponse.success(200, response, "Eligibility evaluated"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }

  public record PreferenceRequest(
      PreferenceState state,
      Long identityId,
      String sourceRef,
      Instant occurredAt,
      String reason,
      String idempotencyKey) {}

  public record SuppressionRequest(
      Long identityId,
      Channel channel,
      SuppressionReason reasonCode,
      String sourceRef,
      Instant occurredAt,
      String reason,
      String idempotencyKey) {}

  public record LiftSuppressionRequest(String reason, String idempotencyKey) {}
}
