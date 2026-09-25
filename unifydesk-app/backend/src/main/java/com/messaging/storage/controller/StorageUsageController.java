package com.messaging.storage.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import com.messaging.storage.dto.StorageUsageResponse;
import com.messaging.storage.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/storage")
@RequiredArgsConstructor
public class StorageUsageController {

  private final StorageQuotaService storageQuotaService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<StorageUsageResponse>> usage(@PathVariable Long companyId) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.MEDIA_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), storageQuotaService.usage(companyId), "Storage usage fetched"));
  }
}
