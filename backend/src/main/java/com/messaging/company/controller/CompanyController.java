package com.messaging.company.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.dto.CompanyResponse;
import com.messaging.company.dto.CompanyUserResponse;
import com.messaging.company.dto.CreateCompanyRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Company deleted"));
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
}
