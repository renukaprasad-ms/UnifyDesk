package com.messaging.managedemail.control.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.managedemail.control.dto.ManagedEmailAdminDtos.ActionRequest;
import com.messaging.managedemail.control.dto.ManagedEmailAdminDtos.ContainmentResponse;
import com.messaging.managedemail.control.service.ManagedEmailAdminContainmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/companies/{companyId}/managed-email")
@RequiredArgsConstructor
public class ManagedEmailAdminController {

  private final ManagedEmailAdminContainmentService containmentService;

  @PostMapping("/suspend")
  public ResponseEntity<ApiResponse<ContainmentResponse>> suspend(
      @PathVariable Long companyId, @Valid @RequestBody ActionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            containmentService.suspend(companyId, request.reason()),
            "Managed Email suspended"));
  }

  @PostMapping("/resume")
  public ResponseEntity<ApiResponse<ContainmentResponse>> resume(
      @PathVariable Long companyId, @Valid @RequestBody ActionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            containmentService.resume(companyId, request.reason()),
            "Managed Email resume queued"));
  }
}
