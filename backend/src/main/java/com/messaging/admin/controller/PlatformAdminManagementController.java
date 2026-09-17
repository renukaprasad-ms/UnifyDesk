package com.messaging.admin.controller;

import com.messaging.admin.dto.*;
import com.messaging.admin.service.PlatformAdminManagementService;
import com.messaging.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminManagementController {
  private final PlatformAdminManagementService service;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<PlatformAdminResponse>>> list(
      @RequestParam(defaultValue = "") String search,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(200, service.list(search, pageable), "Platform admins fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<PlatformAdminResponse>> create(
      @Valid @RequestBody CreatePlatformAdminRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.create(request), "Platform admin created"));
  }

  @PutMapping("/{adminId}")
  public ResponseEntity<ApiResponse<PlatformAdminResponse>> update(
      @PathVariable Long adminId, @Valid @RequestBody UpdatePlatformAdminRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(200, service.update(adminId, request), "Platform admin updated"));
  }
}
