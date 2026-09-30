package com.messaging.contacts.consent;

import com.messaging.common.exception.NotFoundException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.ContactAudiencePermissions;
import com.messaging.contacts.consent.ConsentApiModel.EligibilityResponse;
import com.messaging.contacts.consent.ConsentApiModel.LiftSuppressionRequest;
import com.messaging.contacts.consent.ConsentApiModel.PreferenceRequest;
import com.messaging.contacts.consent.ConsentApiModel.PreferenceResponse;
import com.messaging.contacts.consent.ConsentApiModel.SuppressionRequest;
import com.messaging.contacts.consent.ConsentApiModel.SuppressionResponse;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.EligibilityContext;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSnapshot;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import com.messaging.contacts.consent.ConsentTypes.SuppressionSnapshot;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
public class ConsentController {

  private final ContactPreferenceService preferenceService;
  private final ContactSuppressionService suppressionService;
  private final ContactEligibilityService eligibilityService;
  private final ConsentApiMapper mapper;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/{contactId}/preferences")
  public ResponseEntity<ApiResponse<List<PreferenceResponse>>> preferences(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, ContactAudiencePermissions.CONSENT_READ);
    List<PreferenceResponse> response =
        preferenceService.list(companyId, contactId).stream().map(mapper::toApi).toList();
    return ResponseEntity.ok(
        ApiResponse.success(200, response, "Preferences fetched"));
  }

  @PutMapping("/{contactId}/preferences/{channel}/{purpose}")
  public ResponseEntity<ApiResponse<PreferenceResponse>> setPreference(
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
            mapper.parseOptionalId(request.identityId()),
            channel,
            purpose,
            request.state(),
            request.source(),
            request.sourceRef(),
            request.reason(),
            request.occurredAt(),
            request.idempotencyKey(),
            userId);
    return ResponseEntity.ok(
        ApiResponse.success(200, mapper.toApi(response), "Preference updated"));
  }

  @GetMapping("/{contactId}/suppressions")
  public ResponseEntity<ApiResponse<List<SuppressionResponse>>> suppressions(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, ContactAudiencePermissions.CONSENT_READ);
    List<SuppressionResponse> response =
        suppressionService.list(companyId, contactId).stream()
            .map(item -> mapper.toApi(item, suppressionService.isLiftable(item)))
            .toList();
    return ResponseEntity.ok(
        ApiResponse.success(200, response, "Suppressions fetched"));
  }

  @PostMapping("/{contactId}/suppressions")
  public ResponseEntity<ApiResponse<SuppressionResponse>> suppress(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @RequestBody SuppressionRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.CONSENT_MODIFY);
    SuppressionSnapshot response =
        suppressionService.addManual(
            companyId,
            contactId,
            mapper.parseOptionalId(request.identityId()),
            request.channel(),
            request.reasonCode(),
            request.sourceRef(),
            request.note(),
            request.occurredAt(),
            request.idempotencyKey(),
            userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            mapper.toApi(response, suppressionService.isLiftable(response)),
            "Suppression added"));
  }

  @PostMapping("/{contactId}/suppressions/{suppressionId}/lift")
  public ResponseEntity<ApiResponse<SuppressionResponse>> lift(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @PathVariable long suppressionId,
      @RequestBody(required = false) LiftSuppressionRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.CONSENT_MODIFY);
    SuppressionSnapshot existing =
        suppressionService.list(companyId, contactId).stream()
            .filter(item -> item.id() == suppressionId)
            .findFirst()
            .orElseThrow(() -> new NotFoundException("CONTACT_NOT_FOUND"));
    if (!Objects.equals(existing.contactId(), contactId)) {
      throw new NotFoundException("CONTACT_NOT_FOUND");
    }
    String reason = request == null ? null : request.reason();
    String idempotencyKey = request == null ? null : request.idempotencyKey();
    SuppressionSnapshot response =
        suppressionService.liftFromUser(
            companyId, suppressionId, reason, idempotencyKey, userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            mapper.toApi(response, suppressionService.isLiftable(response)),
            "Suppression lifted"));
  }

  @GetMapping("/{contactId}/eligibility")
  public ResponseEntity<ApiResponse<EligibilityResponse>> eligibility(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @RequestParam Channel channel,
      @RequestParam(defaultValue = "MARKETING") Purpose purpose,
      @RequestParam(required = false) String identityId) {
    require(companyId, ContactAudiencePermissions.CONSENT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            mapper.toApi(
                eligibilityService.evaluate(
                    companyId,
                    contactId,
                    mapper.parseOptionalId(identityId),
                    channel,
                    purpose,
                    new EligibilityContext(null, null, null))),
            "Eligibility evaluated"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
