package com.messaging.admin.controller;
import com.messaging.admin.dto.*;
import com.messaging.admin.service.PlatformCompanyManagementService;
import com.messaging.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/companies")
@RequiredArgsConstructor
public class PlatformCompanyManagementController {
  private final PlatformCompanyManagementService service;
  @GetMapping public ResponseEntity<ApiResponse<Page<PlatformCompanyResponse>>> list(
      @RequestParam(defaultValue="") String search,
      @PageableDefault(size=20, sort="createdAt", direction=Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(200, service.list(search,pageable), "Companies fetched"));
  }
  @PatchMapping("/{companyId}/status") public ResponseEntity<ApiResponse<PlatformCompanyResponse>> status(
      @PathVariable Long companyId,@Valid @RequestBody UpdateCompanyStatusRequest request) {
    return ResponseEntity.ok(ApiResponse.success(200, service.updateStatus(companyId,request), "Company status updated"));
  }
}
