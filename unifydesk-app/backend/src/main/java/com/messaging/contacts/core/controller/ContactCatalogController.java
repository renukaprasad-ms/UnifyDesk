package com.messaging.contacts.core.controller;

import static com.messaging.contacts.core.api.ContactDtos.*;

import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.service.ContactCatalogService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
public class ContactCatalogController {

  private final ContactCatalogService service;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/tags")
  public ResponseEntity<ApiResponse<List<TagResponse>>> tags(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.tags(companyId), "Tags fetched"));
  }

  @PostMapping("/tags")
  public ResponseEntity<ApiResponse<TagResponse>> createTag(
      @PathVariable Long companyId, @Valid @RequestBody TagRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.createTag(companyId, request), "Tag created"));
  }

  @PatchMapping("/tags/{tagId}")
  public ResponseEntity<ApiResponse<TagResponse>> patchTag(
      @PathVariable Long companyId,
      @PathVariable Long tagId,
      @Valid @RequestBody TagRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.patchTag(companyId, tagId, request), "Tag updated"));
  }

  @DeleteMapping("/tags/{tagId}")
  public ResponseEntity<ApiResponse<Void>> archiveTag(
      @PathVariable Long companyId, @PathVariable Long tagId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.archiveTag(companyId, tagId);
    return ResponseEntity.ok(ApiResponse.success(200, "Tag archived"));
  }

  @GetMapping("/custom-fields")
  public ResponseEntity<ApiResponse<List<CustomFieldDefinitionResponse>>> customFields(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.customFields(companyId), "Custom fields fetched"));
  }

  @PostMapping("/custom-fields")
  public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> createCustomField(
      @PathVariable Long companyId,
      @Valid @RequestBody CustomFieldDefinitionRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, service.createCustomField(companyId, request), "Custom field created"));
  }

  @PatchMapping("/custom-fields/{fieldId}")
  public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> patchCustomField(
      @PathVariable Long companyId,
      @PathVariable Long fieldId,
      @Valid @RequestBody CustomFieldDefinitionPatchRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.patchCustomField(companyId, fieldId, request), "Custom field updated"));
  }

  @DeleteMapping("/custom-fields/{fieldId}")
  public ResponseEntity<ApiResponse<Void>> archiveCustomField(
      @PathVariable Long companyId, @PathVariable Long fieldId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.archiveCustomField(companyId, fieldId);
    return ResponseEntity.ok(ApiResponse.success(200, "Custom field archived"));
  }

  @GetMapping("/lists")
  public ResponseEntity<ApiResponse<List<ContactListResponse>>> lists(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.lists(companyId), "Lists fetched"));
  }

  @PostMapping("/lists")
  public ResponseEntity<ApiResponse<ContactListResponse>> createList(
      @PathVariable Long companyId, @Valid @RequestBody ContactListRequest request) {
    long userId = require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, service.createList(companyId, userId, request), "List created"));
  }

  @GetMapping("/lists/{listId}")
  public ResponseEntity<ApiResponse<ContactListResponse>> getList(
      @PathVariable Long companyId, @PathVariable Long listId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.getList(companyId, listId), "List fetched"));
  }

  @PatchMapping("/lists/{listId}")
  public ResponseEntity<ApiResponse<ContactListResponse>> patchList(
      @PathVariable Long companyId,
      @PathVariable Long listId,
      @Valid @RequestBody ContactListRequest request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.patchList(companyId, listId, request), "List updated"));
  }

  @DeleteMapping("/lists/{listId}")
  public ResponseEntity<ApiResponse<Void>> archiveList(
      @PathVariable Long companyId, @PathVariable Long listId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.archiveList(companyId, listId);
    return ResponseEntity.ok(ApiResponse.success(200, "List archived"));
  }

  @GetMapping("/lists/{listId}/members")
  public ResponseEntity<ApiResponse<ContactPage<ContactSummary>>> listMembers(
      @PathVariable Long companyId,
      @PathVariable Long listId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.listMembers(companyId, listId, cursor, size),
            "List members fetched"));
  }

  @PostMapping("/lists/{listId}/members")
  public ResponseEntity<ApiResponse<ContactListResponse>> addListMembers(
      @PathVariable Long companyId,
      @PathVariable Long listId,
      @Valid @RequestBody AddListMembersRequest request) {
    long userId = require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.addListMembers(companyId, listId, userId, request),
            "List members added"));
  }

  @DeleteMapping("/lists/{listId}/members/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeListMember(
      @PathVariable Long companyId,
      @PathVariable Long listId,
      @PathVariable Long contactId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.removeListMember(companyId, listId, contactId);
    return ResponseEntity.ok(ApiResponse.success(200, "List member removed"));
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
