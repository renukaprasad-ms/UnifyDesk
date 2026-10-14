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

  public record CreateAccount(
      @NotBlank String name,
      String website,
      String notes,
      Long ownerUserId,
      java.util.Map<String, Object> customFields) {
    public CreateAccount(String name, String website, String notes, Long ownerUserId) {
      this(name, website, notes, ownerUserId, java.util.Map.of());
    }
  }

  public record CreateTask(
      @NotBlank String title,
      String details,
      String priority,
      Instant dueAt,
      Long assigneeUserId,
      Long contactId,
      Long accountId,
      String columnKey,
      String taskType) {}

  public record UpdateAccount(
      @NotBlank String name,
      String website,
      String notes,
      Long ownerUserId,
      @NotNull Long version,
      java.util.Map<String, Object> customFields) {
    public UpdateAccount(
        String name, String website, String notes, Long ownerUserId, Long version) {
      this(name, website, notes, ownerUserId, version, null);
    }
  }

  public record UpdateTask(
      @NotBlank String title,
      String details,
      @NotBlank String priority,
      Instant dueAt,
      @NotNull Long assigneeUserId,
      Long contactId,
      Long accountId,
      @NotNull Long version,
      String taskType) {}

  public record SetStatus(@NotBlank String status, @NotNull Long version) {}

  private long require(long companyId, String permission) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, permission);
    return actor;
  }

  @GetMapping("/contacts/{contactId}/overview")
  public ResponseEntity<ApiResponse<CrmWorkService.Contact360>> contact360(
      @PathVariable long companyId, @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.contact360(companyId, contactId), "Contact CRM overview fetched"));
  }

  @DeleteMapping("/accounts/{accountId}/contacts/{contactId}")
  public ResponseEntity<ApiResponse<Void>> unlinkContact(
      @PathVariable long companyId, @PathVariable long accountId, @PathVariable long contactId) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_MODIFY);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    service.unlinkContact(companyId, accountId, contactId);
    return ResponseEntity.ok(ApiResponse.success(200, "Contact unlinked"));
  }

  @GetMapping("/accounts")
  public ResponseEntity<ApiResponse<CrmWorkService.Page<CrmWorkService.Account>>> accounts(
      @PathVariable long companyId,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.accounts(companyId, before, size), "Accounts fetched"));
  }

  @GetMapping("/accounts/{accountId}")
  public ResponseEntity<ApiResponse<CrmWorkService.Account>> getAccount(
      @PathVariable long companyId, @PathVariable long accountId) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.getAccount(companyId, accountId), "Account fetched"));
  }

  @GetMapping("/accounts/{accountId}/contact-summaries")
  public ResponseEntity<ApiResponse<java.util.List<CrmWorkService.AccountContact>>>
      accountContactSummaries(@PathVariable long companyId, @PathVariable long accountId) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.accountContactSummaries(companyId, accountId),
            "Account contacts fetched"));
  }

  @PostMapping("/accounts")
  public ResponseEntity<ApiResponse<CrmWorkService.Account>> createAccount(
      @PathVariable long companyId, @Valid @RequestBody CreateAccount request) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.createAccount(
                    companyId,
                    request.name(),
                    request.website(),
                    request.notes(),
                    request.ownerUserId(),
                    request.customFields()),
                "Account created"));
  }

  @PatchMapping("/accounts/{accountId}")
  public ResponseEntity<ApiResponse<CrmWorkService.Account>> updateAccount(
      @PathVariable long companyId,
      @PathVariable long accountId,
      @Valid @RequestBody UpdateAccount request) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.updateAccount(
                companyId,
                accountId,
                request.version(),
                request.name(),
                request.website(),
                request.notes(),
                request.ownerUserId(),
                request.customFields()),
            "Account updated"));
  }

  @PostMapping("/accounts/{accountId}/contacts/{contactId}")
  public ResponseEntity<ApiResponse<Void>> linkContact(
      @PathVariable long companyId, @PathVariable long accountId, @PathVariable long contactId) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_MODIFY);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    service.linkContact(companyId, accountId, contactId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, "Contact linked"));
  }

  @GetMapping("/accounts/{accountId}/contacts")
  public ResponseEntity<ApiResponse<java.util.List<String>>> accountContacts(
      @PathVariable long companyId, @PathVariable long accountId) {
    require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.accountContactIds(companyId, accountId), "Account contacts fetched"));
  }

  @GetMapping("/tasks")
  public ResponseEntity<ApiResponse<CrmWorkService.Page<CrmWorkService.Task>>> tasks(
      @PathVariable long companyId,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) Long assigneeUserId,
      @RequestParam(required = false) String status) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.tasks(companyId, before, size, assigneeUserId, status), "Tasks fetched"));
  }

  @PostMapping("/tasks")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> createTask(
      @PathVariable long companyId, @Valid @RequestBody CreateTask request) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    if (request.contactId() != null) require(companyId, CompanyPermissionKeys.CONTACT_READ);
    if (request.accountId() != null) require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    long assignee = request.assigneeUserId() == null ? actor : request.assigneeUserId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.createTask(
                    companyId,
                    request.title(),
                    request.details(),
                    request.priority() == null ? "NORMAL" : request.priority(),
                    request.dueAt(),
                    assignee,
                    request.contactId(),
                    request.accountId(),
                    actor,
                    request.columnKey(),
                    request.taskType()),
                "Task created"));
  }

  @PatchMapping("/tasks/{taskId}")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> updateTask(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody UpdateTask request) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    if (request.contactId() != null) require(companyId, CompanyPermissionKeys.CONTACT_READ);
    if (request.accountId() != null) require(companyId, CompanyPermissionKeys.CRM_ACCOUNT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.updateTask(
                companyId,
                taskId,
                request.version(),
                request.title(),
                request.details(),
                request.priority(),
                request.dueAt(),
                request.assigneeUserId(),
                request.contactId(),
                request.accountId(),
                actor,
                request.taskType()),
            "Task updated"));
  }

  @PatchMapping("/tasks/{taskId}/status")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> changeTaskStatus(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody SetStatus request) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.changeTaskStatus(companyId, taskId, request.version(), request.status(), actor),
            "Task updated"));
  }

  @GetMapping("/tasks/{taskId}")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> task(
      @PathVariable long companyId, @PathVariable long taskId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.getTask(companyId, taskId), "Task fetched"));
  }

  @DeleteMapping("/tasks/{taskId}")
  public ResponseEntity<ApiResponse<Void>> deleteTask(
      @PathVariable long companyId, @PathVariable long taskId) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    service.deleteTask(companyId, taskId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Task deleted"));
  }

  @GetMapping("/tasks/{taskId}/history")
  public ResponseEntity<ApiResponse<java.util.List<CrmWorkService.TaskEvent>>> taskHistory(
      @PathVariable long companyId, @PathVariable long taskId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    require(companyId, CompanyPermissionKeys.CONTACT_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.taskHistory(companyId, taskId), "Task history fetched"));
  }
}
