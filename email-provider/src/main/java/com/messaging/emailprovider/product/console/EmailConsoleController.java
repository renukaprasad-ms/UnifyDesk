package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.exception.ForbiddenException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.domain.dto.CreateSendingDomainRequest;
import com.messaging.emailprovider.domain.dto.SendingDomainResponse;
import com.messaging.emailprovider.domain.service.SendingDomainService;
import com.messaging.emailprovider.product.api.ProductRequests;
import com.messaging.emailprovider.product.api.ProductResponses;
import com.messaging.emailprovider.product.model.EmailProject;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import com.messaging.emailprovider.product.service.EmailProductAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/console/v1")
public class EmailConsoleController {
  private final EmailProjectRepository projects;
  private final EmailProductAdminService admin;
  private final SendingDomainService domains;

  public EmailConsoleController(EmailProjectRepository projects,
      EmailProductAdminService admin, SendingDomainService domains) {
    this.projects = projects;
    this.admin = admin;
    this.domains = domains;
  }

  @GetMapping("/projects")
  public ApiResponse<List<ProductResponses.Project>> projects(HttpServletRequest req) {
    return ApiResponse.success(200,
        admin.projects(ConsoleAuthFilter.require(req).organizationId()), "Projects");
  }

  @PostMapping("/projects")
  public ResponseEntity<ApiResponse<ProductResponses.Project>> createProject(
      HttpServletRequest req, @Valid @RequestBody ProductRequests.NewProject body) {
    var user = ConsoleAuthFilter.require(req);
    checkManage(user);
    return ResponseEntity.status(HttpStatus.CREATED).body(
        ApiResponse.success(201, admin.createProject(user.organizationId(), body),
            "Project created with sending disabled"));
  }

  @GetMapping("/projects/{projectId}/keys")
  public ApiResponse<List<ProductResponses.ApiKey>> keys(
      HttpServletRequest req, @PathVariable @Positive Long projectId) {
    own(req, projectId);
    return ApiResponse.success(200, admin.keys(projectId), "API keys");
  }

  @PostMapping("/projects/{projectId}/keys")
  public ResponseEntity<ApiResponse<ProductResponses.IssuedKey>> issueKey(
      HttpServletRequest req, @PathVariable @Positive Long projectId,
      @Valid @RequestBody ProductRequests.NewKey body) {
    checkManage(own(req, projectId));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, admin.issue(projectId, body),
            "Store the secret now; it cannot be retrieved later"));
  }

  @DeleteMapping("/projects/{projectId}/keys/{keyId}")
  public ApiResponse<Void> revokeKey(HttpServletRequest req,
      @PathVariable @Positive Long projectId, @PathVariable @Positive Long keyId) {
    checkManage(own(req, projectId));
    admin.revoke(projectId, keyId);
    return ApiResponse.success(200, "Key revoked");
  }

  @GetMapping("/projects/{projectId}/domains")
  public ApiResponse<List<SendingDomainResponse>> domains(HttpServletRequest req,
      @PathVariable @Positive Long projectId) {
    own(req, projectId);
    return ApiResponse.success(200, domains.list(projectId), "Sender domains");
  }

  @PostMapping("/projects/{projectId}/domains")
  public ResponseEntity<ApiResponse<SendingDomainResponse>> createDomain(
      HttpServletRequest req, @PathVariable @Positive Long projectId,
      @Valid @RequestBody CreateSendingDomainRequest body) {
    checkManage(own(req, projectId));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, domains.create(projectId, body),
            "Add the returned DNS records before verification"));
  }

  @PostMapping("/projects/{projectId}/domains/{domainId}/verify")
  public ApiResponse<SendingDomainResponse> verifyDomain(HttpServletRequest req,
      @PathVariable @Positive Long projectId, @PathVariable @Positive Long domainId) {
    checkManage(own(req, projectId));
    return ApiResponse.success(200, domains.verify(projectId, domainId), "DNS verification");
  }

  private ConsoleAuthService.Principal own(HttpServletRequest req, Long projectId) {
    var user = ConsoleAuthFilter.require(req);
    EmailProject project = projects.findById(projectId)
        .orElseThrow(() -> new NotFoundException("Project not found"));
    if (!user.organizationId().equals(project.getOrganizationId())) {
      throw new NotFoundException("Project not found");
    }
    return user;
  }

  private void checkManage(ConsoleAuthService.Principal user) {
    if (!user.role().canManage()) {
      throw new ForbiddenException("Admin or owner permission required");
    }
  }
}
