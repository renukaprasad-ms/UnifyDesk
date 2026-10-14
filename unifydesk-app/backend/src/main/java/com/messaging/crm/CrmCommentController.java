package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm")
@RequiredArgsConstructor
public class CrmCommentController {

  private final CrmCommentService commentService;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  // Task comments
  @GetMapping("/tasks/{taskId}/comments")
  public ResponseEntity<ApiResponse<List<CrmCommentService.CommentDto>>> getTaskComments(
      @PathVariable long companyId, @PathVariable long taskId) {
    require(companyId, CompanyPermissionKeys.CRM_TASK_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, commentService.listComments(companyId, "TASK", taskId), "Comments fetched"));
  }

  @PostMapping("/tasks/{taskId}/comments")
  public ResponseEntity<ApiResponse<CrmCommentService.CommentDto>> addTaskComment(
      @PathVariable long companyId,
      @PathVariable long taskId,
      @Valid @RequestBody CrmCommentService.AddCommentInput input) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                commentService.addComment(companyId, "TASK", taskId, input.body(), actor),
                "Comment added"));
  }

  @DeleteMapping("/tasks/{taskId}/comments/{commentId}")
  public ResponseEntity<ApiResponse<Void>> deleteTaskComment(
      @PathVariable long companyId, @PathVariable long taskId, @PathVariable long commentId) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_TASK_MODIFY);
    commentService.deleteComment(companyId, commentId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Comment deleted"));
  }

  // Deal comments
  @GetMapping("/deals/{dealId}/comments")
  public ResponseEntity<ApiResponse<List<CrmCommentService.CommentDto>>> getDealComments(
      @PathVariable long companyId, @PathVariable long dealId) {
    require(companyId, CompanyPermissionKeys.CRM_DEAL_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, commentService.listComments(companyId, "DEAL", dealId), "Comments fetched"));
  }

  @PostMapping("/deals/{dealId}/comments")
  public ResponseEntity<ApiResponse<CrmCommentService.CommentDto>> addDealComment(
      @PathVariable long companyId,
      @PathVariable long dealId,
      @Valid @RequestBody CrmCommentService.AddCommentInput input) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                commentService.addComment(companyId, "DEAL", dealId, input.body(), actor),
                "Comment added"));
  }

  @DeleteMapping("/deals/{dealId}/comments/{commentId}")
  public ResponseEntity<ApiResponse<Void>> deleteDealComment(
      @PathVariable long companyId, @PathVariable long dealId, @PathVariable long commentId) {
    long actor = require(companyId, CompanyPermissionKeys.CRM_DEAL_MODIFY);
    commentService.deleteComment(companyId, commentId, actor);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Comment deleted"));
  }

  private long require(long companyId, String permission) {
    long actor = currentUser.currentUserId();
    authorization.requirePermission(actor, companyId, permission);
    return actor;
  }
}
