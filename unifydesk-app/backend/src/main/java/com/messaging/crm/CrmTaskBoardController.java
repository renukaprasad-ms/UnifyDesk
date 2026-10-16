package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm")
@RequiredArgsConstructor
public class CrmTaskBoardController {
  private final CrmTaskBoardService board;
  private final CrmWorkService work;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  public record SetColumn(@NotBlank String columnKey, @NotNull Long version) {}

  public record CreateColumn(@NotBlank String name, @NotBlank String statusGroup) {}

  public record RenameColumn(@NotBlank String name, @NotNull Long version) {}

  public record OrderColumns(@NotNull List<CrmTaskBoardService.Order> columns) {}

  private long require(long companyId, String permission) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, permission);
    return actor;
  }

  @GetMapping("/task-columns")
  public ApiResponse<List<CrmTaskBoardService.Column>> columns(@PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    return ApiResponse.success(200, board.columns(companyId), "Steps fetched");
  }

  @PostMapping("/task-columns")
  public ApiResponse<List<CrmTaskBoardService.Column>> create(
      @PathVariable long companyId, @Valid @RequestBody CreateColumn request) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_WORKFLOW_MODIFY);
    return ApiResponse.success(
        200, board.create(companyId, request.name(), request.statusGroup()), "Step created");
  }

  @PatchMapping("/task-columns/{key}")
  public ApiResponse<List<CrmTaskBoardService.Column>> rename(
      @PathVariable long companyId,
      @PathVariable String key,
      @Valid @RequestBody RenameColumn request) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_WORKFLOW_MODIFY);
    return ApiResponse.success(
        200, board.rename(companyId, key, request.version(), request.name()), "Step updated");
  }

  @PutMapping("/task-columns/order")
  public ApiResponse<List<CrmTaskBoardService.Column>> reorder(
      @PathVariable long companyId, @Valid @RequestBody OrderColumns request) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_WORKFLOW_MODIFY);
    return ApiResponse.success(200, board.reorder(companyId, request.columns()), "Steps reordered");
  }

  @DeleteMapping("/task-columns/{key}")
  public ApiResponse<List<CrmTaskBoardService.Column>> remove(
      @PathVariable long companyId, @PathVariable String key, @RequestParam long version) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_WORKFLOW_MODIFY);
    return ApiResponse.success(200, board.remove(companyId, key, version), "Step removed");
  }

  private boolean canMoveTask(long actor, long companyId, CrmWorkService.Task task) {
    if (authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_TASK_STATUS)
        || authorization.hasPermission(actor, companyId, CompanyPermissionKeys.CRM_TASK_MODIFY)) {
      return true;
    }
    String actorStr = String.valueOf(actor);
    return actorStr.equals(task.assigneeUserId()) || actorStr.equals(task.reporterUserId());
  }

  @PatchMapping("/tasks/{taskId}/column")
  public ApiResponse<CrmWorkService.Task> move(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody SetColumn request) {
    long actor = currentUser.currentUserId();
    CrmWorkService.Task existing = work.getTask(companyId, taskId);
    if (!canMoveTask(actor, companyId, existing)) {
      throw new com.messaging.common.exception.ForbiddenException(
          "You do not have permission to move this task");
    }
    return ApiResponse.success(
        200,
        work.changeTaskColumn(companyId, taskId, request.version(), request.columnKey(), actor),
        "Task moved");
  }
}
