package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm")
@RequiredArgsConstructor
public class CrmWorkController {
  private final CrmWorkService service;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  public record CreateAccount(@NotBlank String name, String website, String notes, Long ownerUserId) {}
  public record CreateTask(@NotBlank String title, String details, String priority,
                           Instant dueAt, Long assigneeUserId, Long contactId, Long accountId) {}
  public record SetStatus(@NotBlank String status, @NotNull Long version) {}

  private long require(long companyId, String permission) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, permission);
    return actor;
  }

  @GetMapping("/contacts/{contactId}/overview")
  public ResponseEntity<ApiResponse<CrmWorkService.Contact360>> contact360(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(ApiResponse.success(
        200, service.contact360(companyId, contactId), "Contact CRM overview fetched"));
  }

  @DeleteMapping("/accounts/{accountId}/contacts/{contactId}")
  public ResponseEntity<ApiResponse<Void>> unlinkContact(
      @PathVariable long companyId, @PathVariable long accountId, @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.unlinkContact(companyId, accountId, contactId);
    return ResponseEntity.ok(ApiResponse.success(200, "Contact unlinked"));
  }

  @GetMapping("/accounts")
  public ResponseEntity<ApiResponse<CrmWorkService.Page<CrmWorkService.Account>>> accounts(
      @PathVariable long companyId, @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.accounts(companyId, before, size), "Accounts fetched"));
  }

  @PostMapping("/accounts")
  public ResponseEntity<ApiResponse<CrmWorkService.Account>> createAccount(
      @PathVariable long companyId, @Valid @RequestBody CreateAccount request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201,
        service.createAccount(companyId, request.name(), request.website(), request.notes(), request.ownerUserId()),
        "Account created"));
  }

  @PostMapping("/accounts/{accountId}/contacts/{contactId}")
  public ResponseEntity<ApiResponse<Void>> linkContact(
      @PathVariable long companyId, @PathVariable long accountId, @PathVariable long contactId) {
    long actor = require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    service.linkContact(companyId, accountId, contactId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, "Contact linked"));
  }

  @GetMapping("/accounts/{accountId}/contacts")
  public ResponseEntity<ApiResponse<java.util.List<String>>> accountContacts(
      @PathVariable long companyId, @PathVariable long accountId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.accountContactIds(companyId, accountId), "Account contacts fetched"));
  }

  @GetMapping("/tasks")
  public ResponseEntity<ApiResponse<CrmWorkService.Page<CrmWorkService.Task>>> tasks(
      @PathVariable long companyId, @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) Long assigneeUserId) {
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.tasks(companyId, before, size, assigneeUserId), "Tasks fetched"));
  }

  @PostMapping("/tasks")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> createTask(
      @PathVariable long companyId, @Valid @RequestBody CreateTask request) {
    long actor = require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    long assignee = request.assigneeUserId() == null ? actor : request.assigneeUserId();
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201,
        service.createTask(companyId, request.title(), request.details(),
            request.priority() == null ? "NORMAL" : request.priority(),
            request.dueAt(), assignee, request.contactId(), request.accountId(), actor),
        "Task created"));
  }

  @PatchMapping("/tasks/{taskId}/status")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> changeTaskStatus(
      @PathVariable long companyId, @PathVariable long taskId, @Valid @RequestBody SetStatus request) {
    require(companyId, CompanyPermissionKeys.CONTACT_MODIFY);
    return ResponseEntity.ok(ApiResponse.success(200,
        service.changeTaskStatus(companyId, taskId, request.version(), request.status()), "Task updated"));
  }
}
