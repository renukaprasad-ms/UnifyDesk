package com.messaging.company.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.dto.AssignCompanyRoleRequest;
import com.messaging.company.dto.CompanyIamPermissionsResponse;
import com.messaging.company.dto.CompanyMemberRoleAssignmentResponse;
import com.messaging.company.dto.CompanyRoleResponse;
import com.messaging.company.dto.CreateCompanyRoleRequest;
import com.messaging.company.dto.PermissionDefinitionResponse;
import com.messaging.company.dto.UpdateCompanyRoleRequest;
import com.messaging.company.service.CompanyRoleManagementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/iam")
@RequiredArgsConstructor
public class CompanyRoleController {

  private final CompanyRoleManagementService roleManagementService;

  @GetMapping("/permissions")
  public ResponseEntity<ApiResponse<List<PermissionDefinitionResponse>>> permissions(
      @PathVariable Long companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            roleManagementService.permissionCatalog(companyId),
            "Permission catalog fetched"));
  }

  @GetMapping("/permissions/me")
  public ResponseEntity<ApiResponse<CompanyIamPermissionsResponse>> myPermissions(
      @PathVariable Long companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            roleManagementService.myPermissions(companyId),
            "Effective permissions fetched"));
  }

  @PostMapping("/roles")
  public ResponseEntity<ApiResponse<CompanyRoleResponse>> createRole(
      @PathVariable Long companyId, @Valid @RequestBody CreateCompanyRoleRequest request) {
    CompanyRoleResponse response = roleManagementService.createRole(companyId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Role created"));
  }

  @GetMapping("/roles")
  public ResponseEntity<ApiResponse<List<CompanyRoleResponse>>> roles(
      @PathVariable Long companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), roleManagementService.roles(companyId), "Roles fetched"));
  }

  @GetMapping("/roles/{roleId}")
  public ResponseEntity<ApiResponse<CompanyRoleResponse>> role(
      @PathVariable Long companyId, @PathVariable Long roleId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), roleManagementService.role(companyId, roleId), "Role fetched"));
  }

  @PutMapping("/roles/{roleId}")
  public ResponseEntity<ApiResponse<CompanyRoleResponse>> updateRole(
      @PathVariable Long companyId,
      @PathVariable Long roleId,
      @Valid @RequestBody UpdateCompanyRoleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            roleManagementService.updateRole(companyId, roleId, request),
            "Role updated"));
  }

  @DeleteMapping("/roles/{roleId}")
  public ResponseEntity<ApiResponse<Void>> deleteRole(
      @PathVariable Long companyId, @PathVariable Long roleId) {
    roleManagementService.deleteRole(companyId, roleId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Role deleted"));
  }

  @PostMapping("/members/{userId}/roles/{roleId}")
  public ResponseEntity<ApiResponse<CompanyMemberRoleAssignmentResponse>> assignRole(
      @PathVariable Long companyId,
      @PathVariable Long userId,
      @PathVariable Long roleId,
      @RequestBody(required = false) AssignCompanyRoleRequest request) {
    CompanyMemberRoleAssignmentResponse response =
        roleManagementService.assignRole(companyId, userId, roleId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Role assigned"));
  }

  @DeleteMapping("/members/{userId}/roles/{roleId}")
  public ResponseEntity<ApiResponse<Void>> revokeRole(
      @PathVariable Long companyId, @PathVariable Long userId, @PathVariable Long roleId) {
    roleManagementService.revokeRole(companyId, userId, roleId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Role revoked"));
  }

  @GetMapping("/members/{userId}/roles")
  public ResponseEntity<ApiResponse<List<CompanyMemberRoleAssignmentResponse>>> memberRoles(
      @PathVariable Long companyId, @PathVariable Long userId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            roleManagementService.memberRoles(companyId, userId),
            "Member roles fetched"));
  }
}
