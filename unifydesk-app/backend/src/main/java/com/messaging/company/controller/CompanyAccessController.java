package com.messaging.company.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.dto.AccessProfileResponse;
import com.messaging.company.dto.CompanyPermissionResponse;
import com.messaging.company.dto.CreateAccessProfileRequest;
import com.messaging.company.dto.GrantAccessProfileRequest;
import com.messaging.company.dto.GrantPermissionOverrideRequest;
import com.messaging.company.service.CompanyAccessService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}")
@RequiredArgsConstructor
public class CompanyAccessController {

  private final CompanyAccessService companyAccessService;

  @GetMapping("/permissions/me")
  public ResponseEntity<ApiResponse<CompanyPermissionResponse>> myPermissions(
      @PathVariable Long companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyAccessService.myPermissions(companyId),
            "Permissions fetched"));
  }

  @PostMapping("/members/{userId}/permissions")
  public ResponseEntity<ApiResponse<Void>> grantPermissionOverride(
      @PathVariable Long companyId,
      @PathVariable Long userId,
      @Valid @RequestBody GrantPermissionOverrideRequest request) {
    companyAccessService.grantPermissionOverride(companyId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Permission override granted"));
  }

  @PostMapping("/access-profiles")
  public ResponseEntity<ApiResponse<AccessProfileResponse>> createProfile(
      @PathVariable Long companyId, @Valid @RequestBody CreateAccessProfileRequest request) {
    AccessProfileResponse response = companyAccessService.createProfile(companyId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Access profile created"));
  }

  @GetMapping("/access-profiles")
  public ResponseEntity<ApiResponse<List<AccessProfileResponse>>> profiles(
      @PathVariable Long companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            companyAccessService.profiles(companyId),
            "Access profiles fetched"));
  }

  @PostMapping("/members/{userId}/access-profiles/{profileId}")
  public ResponseEntity<ApiResponse<Void>> grantAccessProfile(
      @PathVariable Long companyId,
      @PathVariable Long userId,
      @PathVariable Long profileId,
      @Valid @RequestBody GrantAccessProfileRequest request) {
    companyAccessService.grantAccessProfile(companyId, userId, profileId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Access profile granted"));
  }
}
