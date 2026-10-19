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
  private final CrmSubtaskService subtaskService;
  private final CrmTaskAttachmentService attachmentService;
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
      Long dealId,
      Integer estimatedMinutes,
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
      Long dealId,
      Integer estimatedMinutes,
      Integer loggedMinutes,
      @NotNull Long version,
      String taskType) {}

  public record SetStatus(@NotBlank String status, @NotNull Long version) {}

  public record LogTimeRequest(@NotNull Integer minutes, @NotNull Long version) {}

  private long require(long companyId, String permission) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, permission);
    return actor;
  }

  private long requireAny(long companyId, String... permissionKeys) {
    long actor = currentUser.currentUserId();
    for (String key : permissionKeys) {
      if (authorization.hasPermission(actor, companyId, key)) {
        return actor;
      }
    }
    throw new com.messaging.common.exception.ForbiddenException(
        "Company permission denied: " + String.join(" or ", permissionKeys));
  }

  private boolean canModifyTask(long actor, long companyId, CrmWorkService.Task task, String requiredPermission) {
    if (authorization.hasPermission(actor, companyId, requiredPermission)
        || authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_TASK_MODIFY)) {
      return true;
    }
    String actorStr = String.valueOf(actor);
    return actorStr.equals(task.assigneeUserId()) || actorStr.equals(task.reporterUserId());
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
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long reporterUserId,
      @RequestParam(required = false) Long dealId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.tasks(companyId, before, size, assigneeUserId, status, reporterUserId, dealId),
            "Tasks fetched"));
  }

  @PostMapping("/tasks")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> createTask(
      @PathVariable long companyId, @Valid @RequestBody CreateTask request) {
    long actor = requireAny(companyId, CompanyPermissionKeys.CRM_TASK_CREATE, CompanyPermissionKeys.CRM_TASK_MODIFY);
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
                    request.dealId(),
                    request.estimatedMinutes(),
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
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to edit this task");
    }
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
                request.dealId(),
                request.estimatedMinutes(),
                request.loggedMinutes(),
                actor,
                request.taskType()),
            "Task updated"));
  }

  @PatchMapping("/tasks/{taskId}/status")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> changeTaskStatus(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody SetStatus request) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_STATUS)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to change the status of this task");
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.changeTaskStatus(companyId, taskId, request.version(), request.status(), actor),
            "Task updated"));
  }

  @PostMapping("/tasks/{taskId}/log-time")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> logTime(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody LogTimeRequest request) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to log time on this task");
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.logTime(companyId, taskId, request.version(), request.minutes(), actor),
            "Time logged"));
  }

  @GetMapping("/tasks/{taskId}")
  public ResponseEntity<ApiResponse<CrmWorkService.Task>> task(
      @PathVariable long companyId, @PathVariable long taskId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.getTask(companyId, taskId), "Task fetched"));
  }

  @DeleteMapping("/tasks/{taskId}")
  public ResponseEntity<ApiResponse<Void>> deleteTask(
      @PathVariable long companyId, @PathVariable long taskId) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    boolean hasGlobalDelete =
        authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_TASK_DELETE)
            || authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    boolean isReporter = String.valueOf(actor).equals(existing.reporterUserId());
    if (!hasGlobalDelete && !isReporter) {
      throw new com.messaging.common.exception.ForbiddenException(
          "Only managers or the task reporter can delete this task");
    }
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

  // --- Subtasks / Checklists ---
  @GetMapping("/tasks/{taskId}/subtasks")
  public ResponseEntity<ApiResponse<java.util.List<CrmSubtaskService.Subtask>>> getSubtasks(
      @PathVariable long companyId, @PathVariable long taskId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, subtaskService.list(companyId, taskId), "Subtasks fetched"));
  }

  @PostMapping("/tasks/{taskId}/subtasks")
  public ResponseEntity<ApiResponse<CrmSubtaskService.Subtask>> createSubtask(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody CrmSubtaskService.CreateSubtaskInput input) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to add subtasks to this task");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                subtaskService.create(companyId, taskId, input.title(), actor),
                "Subtask created"));
  }

  @PatchMapping("/tasks/{taskId}/subtasks/{subtaskId}/toggle")
  public ResponseEntity<ApiResponse<CrmSubtaskService.Subtask>> toggleSubtask(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @PathVariable long subtaskId,
      @Valid @RequestBody CrmSubtaskService.ToggleSubtaskInput input) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_STATUS)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to toggle subtasks on this task");
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            subtaskService.toggle(companyId, taskId, subtaskId, input.completed(), actor),
            "Subtask toggled"));
  }

  @PatchMapping("/tasks/{taskId}/subtasks/{subtaskId}")
  public ResponseEntity<ApiResponse<CrmSubtaskService.Subtask>> updateSubtask(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @PathVariable long subtaskId,
      @Valid @RequestBody CrmSubtaskService.UpdateSubtaskInput input) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to update subtasks on this task");
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            subtaskService.update(companyId, taskId, subtaskId, input.title(), actor),
            "Subtask updated"));
  }

  @DeleteMapping("/tasks/{taskId}/subtasks/{subtaskId}")
  public ResponseEntity<ApiResponse<Void>> deleteSubtask(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @PathVariable long subtaskId) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to delete subtasks on this task");
    }
    subtaskService.delete(companyId, taskId, subtaskId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Subtask deleted"));
  }

  @PostMapping("/tasks/{taskId}/subtasks/reorder")
  public ResponseEntity<ApiResponse<Void>> reorderSubtasks(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody CrmSubtaskService.ReorderSubtasksInput input) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to reorder subtasks on this task");
    }
    subtaskService.reorder(companyId, taskId, input.subtaskIds(), actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Subtasks reordered"));
  }

  // --- Task Attachments ---
  @GetMapping("/tasks/{taskId}/attachments")
  public ResponseEntity<ApiResponse<java.util.List<CrmTaskAttachmentService.TaskAttachmentDto>>> getTaskAttachments(
      @PathVariable long companyId, @PathVariable long taskId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, attachmentService.list(companyId, taskId), "Attachments fetched"));
  }

  @PostMapping("/tasks/{taskId}/attachments")
  public ResponseEntity<ApiResponse<CrmTaskAttachmentService.TaskAttachmentDto>> addTaskAttachment(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody CrmTaskAttachmentService.AttachInput input) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to upload attachments to this task");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                attachmentService.attach(companyId, taskId, input, actor),
                "Attachment uploaded"));
  }

  @DeleteMapping("/tasks/{taskId}/attachments/{attachmentId}")
  public ResponseEntity<ApiResponse<Void>> deleteTaskAttachment(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @PathVariable long attachmentId) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = service.getTask(companyId, taskId);
    if (!canModifyTask(actor, companyId, existing, CompanyPermissionKeys.CRM_TASK_EDIT)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to delete attachments on this task");
    }
    attachmentService.delete(companyId, taskId, attachmentId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Attachment deleted"));
  }
}
