package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.service.EmailProductAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/internal/v1/email-product")
public class EmailProductAdminController {
  private final EmailProductAdminService admin;

  public EmailProductAdminController(EmailProductAdminService admin) {
    this.admin = admin;
  }

  @PostMapping("/organizations")
  public ResponseEntity<ApiResponse<ProductResponses.Organization>> organization(
      @Valid @RequestBody ProductRequests.NewOrganization request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, admin.createOrganization(request), "Organization created"));
  }

  @GetMapping("/organizations/{organizationId}")
  public ApiResponse<ProductResponses.Organization> organization(
      @PathVariable @Positive Long organizationId) {
    return ApiResponse.success(200, admin.organization(organizationId), "Organization");
  }

  @PostMapping("/organizations/{organizationId}/status")
  public ApiResponse<ProductResponses.Organization> organizationStatus(
      @PathVariable @Positive Long organizationId,
      @Valid @RequestBody ProductRequests.StatusSwitch request) {
    return ApiResponse.success(
        200,
        admin.updateOrganizationStatus(organizationId, request.status()),
        "Organization status updated");
  }

  @PostMapping("/organizations/{organizationId}/projects")
  public ResponseEntity<ApiResponse<ProductResponses.Project>> project(
      @PathVariable @Positive Long organizationId,
      @Valid @RequestBody ProductRequests.NewProject request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, admin.createProject(organizationId, request), "Project created"));
  }

  @GetMapping("/organizations/{organizationId}/projects")
  public ApiResponse<List<ProductResponses.Project>> projects(
      @PathVariable @Positive Long organizationId) {
    return ApiResponse.success(200, admin.projects(organizationId), "Projects");
  }

  @GetMapping("/projects/{projectId}")
  public ApiResponse<ProductResponses.Project> project(@PathVariable @Positive Long projectId) {
    return ApiResponse.success(200, admin.project(projectId), "Project");
  }

  @PostMapping("/projects/{projectId}/status")
  public ApiResponse<ProductResponses.Project> projectStatus(
      @PathVariable @Positive Long projectId,
      @Valid @RequestBody ProductRequests.StatusSwitch request) {
    return ApiResponse.success(
        200, admin.updateProjectStatus(projectId, request.status()), "Project status updated");
  }

  @PostMapping("/projects/{projectId}/keys")
  public ResponseEntity<ApiResponse<ProductResponses.IssuedKey>> issueKey(
      @PathVariable @Positive Long projectId, @Valid @RequestBody ProductRequests.NewKey request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201, admin.issue(projectId, request), "Secret returned once; store it securely"));
  }

  @GetMapping("/projects/{projectId}/keys")
  public ApiResponse<List<ProductResponses.ApiKey>> keys(@PathVariable @Positive Long projectId) {
    return ApiResponse.success(200, admin.keys(projectId), "API keys (secrets hidden)");
  }

  @DeleteMapping("/projects/{projectId}/keys/{keyId}")
  public ApiResponse<Void> revoke(
      @PathVariable @Positive Long projectId, @PathVariable @Positive Long keyId) {
    admin.revoke(projectId, keyId);
    return ApiResponse.success(200, "API key revoked");
  }

  @PostMapping("/projects/{projectId}/sending")
  public ApiResponse<ProductResponses.Project> sending(
      @PathVariable @Positive Long projectId,
      @Valid @RequestBody ProductRequests.SendingSwitch request) {
    return ApiResponse.success(
        200, admin.setSending(projectId, request.enabled()), "Project sending setting updated");
  }
}
