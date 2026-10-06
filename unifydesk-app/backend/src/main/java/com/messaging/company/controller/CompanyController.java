package com.messaging.company.controller;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.dto.CompanyResponse;
import com.messaging.company.dto.CompanyUserResponse;
import com.messaging.company.dto.CreateCompanyRequest;
import com.messaging.company.dto.TransferCompanyOwnershipRequest;
import com.messaging.company.dto.UpdateCompanyMemberRoleRequest;
import com.messaging.company.dto.UpdateCompanyMemberStatusRequest;
import com.messaging.company.dto.UpdateCompanyRequest;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.company.service.CompanyService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

  private final CompanyService companyService;
  private final CompanyAuthorizationService companyAuthorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping
  public ResponseEntity<ApiResponse<CompanyResponse>> create(
      @Valid @RequestBody CreateCompanyRequest request) {
    CompanyResponse response = companyService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Company created"));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<List<CompanyResponse>>> myCompanies() {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), companyService.myCompanies(), "Companies fetched"));
  }

  @GetMapping("/{companyId}")
  public ResponseEntity<ApiResponse<CompanyResponse>> company(@PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    companyAuthorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.COMPANY_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyService.companyForUser(companyId, userId),
            "Company fetched"));
  }

  @PutMapping("/{companyId}")
  public ResponseEntity<ApiResponse<CompanyResponse>> update(
      @PathVariable Long companyId, @Valid @RequestBody UpdateCompanyRequest request) {
    Long userId = currentUserService.currentUserId();
    companyAuthorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.COMPANY_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyService.update(companyId, userId, request),
            "Company updated"));
  }

  @DeleteMapping("/{companyId}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    companyAuthorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.COMPANY_DELETE);
    companyService.delete(companyId, userId);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Company deletion scheduled"));
  }

  @GetMapping("/{companyId}/users")
  public ResponseEntity<ApiResponse<List<CompanyUserResponse>>> companyUsers(
      @PathVariable Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.USER_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyService.companyUsers(companyId),
            "Company users fetched"));
  }

  @PatchMapping("/{companyId}/members/{userId}/role")
  public ResponseEntity<ApiResponse<CompanyUserResponse>> updateMemberRole(
      @PathVariable Long companyId,
      @PathVariable Long userId,
      @Valid @RequestBody UpdateCompanyMemberRoleRequest request) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.USER_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyService.updateMemberRole(
                companyId, currentUserService.currentUserId(), userId, request.role()),
            "Member role updated"));
  }

  @PatchMapping("/{companyId}/members/{userId}/status")
  public ResponseEntity<ApiResponse<CompanyUserResponse>> updateMemberStatus(
      @PathVariable Long companyId,
      @PathVariable Long userId,
      @Valid @RequestBody UpdateCompanyMemberStatusRequest request) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.USER_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyService.updateMemberStatus(
                companyId, currentUserService.currentUserId(), userId, request.status()),
            "Member status updated"));
  }

  @DeleteMapping("/{companyId}/members/{userId}")
  public ResponseEntity<ApiResponse<Void>> removeMember(
      @PathVariable Long companyId, @PathVariable Long userId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(), companyId, CompanyPermissionKeys.USER_REMOVE);
    companyService.removeMember(companyId, currentUserService.currentUserId(), userId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Member removed"));
  }

  @PostMapping("/{companyId}/members/me/leave")
  public ResponseEntity<ApiResponse<Void>> leave(@PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    companyAuthorizationService.requireActiveMembership(userId, companyId);
    companyService.leave(companyId, userId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Company left"));
  }

  @PostMapping("/{companyId}/ownership-transfer")
  public ResponseEntity<ApiResponse<Void>> transferOwnership(
      @PathVariable Long companyId,
      @Valid @RequestBody TransferCompanyOwnershipRequest request) {
    Long actorId = currentUserService.currentUserId();
    companyAuthorizationService.requirePermission(
        actorId, companyId, CompanyPermissionKeys.COMPANY_TRANSFER_OWNERSHIP);
    long targetUserId;
    try {
      targetUserId = Long.parseLong(request.targetUserId());
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid target user id");
    }
    companyService.transferOwnership(
        companyId, actorId, targetUserId, request.currentPassword());
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Ownership transferred"));
  }
}
