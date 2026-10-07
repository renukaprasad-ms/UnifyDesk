package com.messaging.contacts.core.controller;

import static com.messaging.contacts.core.api.ContactDtos.*;

import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.service.ContactService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
public class ContactController {

  private final ContactService service;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<ContactPage<ContactSummary>>> search(
      @PathVariable Long companyId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) ContactStatus status,
      @RequestParam(required = false) Long tagId,
      @RequestParam(required = false) Long listId,
      @RequestParam(required = false) Instant createdAfter,
      @RequestParam(required = false) Instant createdBefore,
      @RequestParam(defaultValue = "UPDATED_DESC") ContactSort sort) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.search(
                companyId,
                new SearchRequest(
                    cursor,
                    size,
                    q,
                    status,
                    tagId,
                    listId,
                    createdAfter,
                    createdBefore,
                    sort)),
            "Contacts fetched"));
  }

  @GetMapping("/dashboard-summary")
  public ResponseEntity<ApiResponse<ContactDashboardSummary>> dashboardSummary(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.dashboardSummary(companyId), "Contact dashboard fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ContactDetail>> create(
      @PathVariable Long companyId, @Valid @RequestBody CreateContactRequest request) {
    long userId = require(companyId, CompanyPermissionKeys.CONTACT_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, service.create(companyId, userId, request), "Contact created"));
  }

  @GetMapping("/{contactId}")
  public ResponseEntity<ApiResponse<ContactDetail>> get(
      @PathVariable Long companyId, @PathVariable Long contactId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.get(companyId, contactId), "Contact fetched"));
  }

  @PatchMapping("/{contactId}")
  public ResponseEntity<ApiResponse<ContactDetail>> patch(
      @PathVariable Long companyId,
      @PathVariable Long contactId,
      @Valid @RequestBody PatchContactRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.patch(companyId, contactId, request), "Contact updated"));
  }

  @DeleteMapping("/{contactId}")
  public ResponseEntity<ApiResponse<DeleteResponse>> delete(
      @PathVariable Long companyId, @PathVariable Long contactId) {
    long userId = require(companyId, CompanyPermissionKeys.CONTACT_DELETE);
    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                202, service.delete(companyId, contactId, userId), "Contact deletion queued"));
  }

  @PostMapping("/{contactId}/identities")
  public ResponseEntity<ApiResponse<IdentityResponse>> addIdentity(
      @PathVariable Long companyId,
      @PathVariable Long contactId,
      @Valid @RequestBody CreateIdentityRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.addIdentity(companyId, contactId, request),
                "Contact identity created"));
  }

  @PostMapping("/{contactId}/identities/{identityId}/primary")
  public ResponseEntity<ApiResponse<IdentityResponse>> primaryIdentity(
      @PathVariable Long companyId,
      @PathVariable Long contactId,
      @PathVariable Long identityId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.setPrimary(companyId, contactId, identityId),
            "Primary identity updated"));
  }

  @DeleteMapping("/{contactId}/identities/{identityId}")
  public ResponseEntity<ApiResponse<Void>> deleteIdentity(
      @PathVariable Long companyId,
      @PathVariable Long contactId,
      @PathVariable Long identityId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.deleteIdentity(companyId, contactId, identityId);
    return ResponseEntity.ok(ApiResponse.success(200, "Contact identity disabled"));
  }

  @PostMapping("/{contactId}/tags/{tagId}")
  public ResponseEntity<ApiResponse<Void>> addTag(
      @PathVariable Long companyId,
      @PathVariable Long contactId,
      @PathVariable Long tagId) {
    long userId = require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.addTag(companyId, contactId, tagId, userId);
    return ResponseEntity.ok(ApiResponse.success(200, "Tag assigned"));
  }

  @DeleteMapping("/{contactId}/tags/{tagId}")
  public ResponseEntity<ApiResponse<Void>> removeTag(
      @PathVariable Long companyId,
      @PathVariable Long contactId,
      @PathVariable Long tagId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.removeTag(companyId, contactId, tagId);
    return ResponseEntity.ok(ApiResponse.success(200, "Tag removed"));
  }

  @PostMapping("/merge")
  public ResponseEntity<ApiResponse<MergeResponse>> merge(
      @PathVariable Long companyId, @Valid @RequestBody MergeRequest request) {
    long userId = require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.merge(companyId, userId, request), "Contacts merged"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUserService.currentUserId();
    try {
      authorizationService.requirePermission(userId, companyId, permission);
    } catch (ForbiddenException exception) {
      throw ContactCoreException.permissionDenied();
    }
    return userId;
  }
}
