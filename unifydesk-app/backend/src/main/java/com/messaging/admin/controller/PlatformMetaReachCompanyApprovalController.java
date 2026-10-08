package com.messaging.admin.controller;

import com.messaging.admin.service.PlatformMetaReachCompanyApprovalService;
import com.messaging.admin.service.PlatformMetaReachCompanyApprovalService.Approval;
import com.messaging.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/managed-providers/sms/companies")
@RequiredArgsConstructor
public class PlatformMetaReachCompanyApprovalController {
  private final PlatformMetaReachCompanyApprovalService service;
  public record ChangeRequest(boolean active,String peId,String evidenceReference,
      @NotBlank @Size(min=8,max=500) String reason) {}

  @GetMapping("/{companyId}/approval")
  public ResponseEntity<ApiResponse<Approval>> get(@PathVariable Long companyId) {
    return ResponseEntity.ok(ApiResponse.success(200,service.get(companyId),
        "MetaReach company approval fetched"));
  }
  @PutMapping("/{companyId}/approval")
  public ResponseEntity<ApiResponse<Approval>> set(@PathVariable Long companyId,
      @Valid @RequestBody ChangeRequest request) {
    return ResponseEntity.ok(ApiResponse.success(200,service.set(companyId,request.peId(),
        request.evidenceReference(),request.reason(),request.active()),
        "MetaReach company approval updated"));
  }
}
