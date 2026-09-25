package com.messaging.admin.controller;

import com.messaging.admin.dto.AdminCompanyAssignmentResponse;
import com.messaging.admin.dto.AssignAdminCompanyRequest;
import com.messaging.admin.service.AdminCompanyService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.dto.CompanyResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCompanyController {

  private final AdminCompanyService adminCompanyService;

  @GetMapping("/companies")
  public ResponseEntity<ApiResponse<List<CompanyResponse>>> visibleCompanies() {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), adminCompanyService.visibleCompanies(), "Companies fetched"));
  }

  @PostMapping("/company-assignments")
  public ResponseEntity<ApiResponse<AdminCompanyAssignmentResponse>> assignCompany(
      @Valid @RequestBody AssignAdminCompanyRequest request) {
    AdminCompanyAssignmentResponse response = adminCompanyService.assignCompany(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), response, "Admin company assignment saved"));
  }
}
