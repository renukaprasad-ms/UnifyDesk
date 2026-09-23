package com.messaging.ads.google;

import com.messaging.ads.google.dto.GoogleAdsAuthorizationResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/google")
@RequiredArgsConstructor
public class GoogleAdsConnectionController {

  private final GoogleAdsConnectionService connectionService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<GoogleAdsAuthorizationResponse>> authorize(
      @PathVariable Long companyId,
      @RequestParam(required = false) String returnTo) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(
        userId, companyId, CompanyPermissionKeys.ADS_CONNECT);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            connectionService.authorization(companyId, userId, returnTo),
            "Google Ads authorization created"));
  }
}
