package com.messaging.ads.optimization;

import com.messaging.ads.optimization.dto.AdExperimentOverviewResponse;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyEvaluation;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyRequest;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyResponse;
import com.messaging.ads.optimization.dto.FirstPartyAudienceBuildRequest;
import com.messaging.ads.optimization.dto.FirstPartyAudienceBuildResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/ads/optimization")
@RequiredArgsConstructor
public class AdOptimizationController {

  private final AdExperimentOverviewService experimentOverviewService;
  private final AdOptimizationPolicyService policyService;
  private final FirstPartyAudienceBuilderService audienceBuilderService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/experiments")
  public ResponseEntity<ApiResponse<AdExperimentOverviewResponse>> experiments(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            experimentOverviewService.overview(companyId),
            "Unified provider experiment overview fetched"));
  }

  @GetMapping("/policies")
  public ResponseEntity<ApiResponse<List<AdOptimizationPolicyResponse>>> policies(
      @PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.ADS_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, policyService.list(companyId), "Ad optimization policies fetched"));
  }

  @PostMapping("/policies")
  public ResponseEntity<ApiResponse<AdOptimizationPolicyResponse>> createPolicy(
      @PathVariable Long companyId,
      @Valid @RequestBody AdOptimizationPolicyRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                policyService.create(companyId, userId, request),
                "Ad optimization policy created"));
  }

  @PutMapping("/policies/{policyId}")
  public ResponseEntity<ApiResponse<AdOptimizationPolicyResponse>> updatePolicy(
      @PathVariable Long companyId,
      @PathVariable Long policyId,
      @Valid @RequestBody AdOptimizationPolicyRequest request) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            policyService.update(companyId, policyId, request),
            "Ad optimization policy updated"));
  }

  @PatchMapping("/policies/{policyId}/enabled")
  public ResponseEntity<ApiResponse<AdOptimizationPolicyResponse>> setPolicyEnabled(
      @PathVariable Long companyId,
      @PathVariable Long policyId,
      @RequestParam boolean enabled) {
    require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            policyService.setEnabled(companyId, policyId, enabled),
            enabled
                ? "Ad optimization policy enabled"
                : "Ad optimization policy disabled"));
  }

  @PostMapping("/policies/{policyId}:evaluate")
  public ResponseEntity<ApiResponse<AdOptimizationPolicyEvaluation>> evaluatePolicy(
      @PathVariable Long companyId,
      @PathVariable Long policyId) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            policyService.evaluate(companyId, policyId, userId),
            "Ad optimization policy evaluated"));
  }

  @PostMapping("/policies/{policyId}:approve")
  public ResponseEntity<ApiResponse<AdOptimizationPolicyEvaluation>> approvePolicy(
      @PathVariable Long companyId,
      @PathVariable Long policyId) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            policyService.approve(companyId, policyId, userId),
            "Ad optimization policy action approved"));
  }

  @PostMapping("/first-party-audiences")
  public ResponseEntity<ApiResponse<FirstPartyAudienceBuildResponse>> buildFirstPartyAudience(
      @PathVariable Long companyId,
      @Valid @RequestBody FirstPartyAudienceBuildRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.ADS_CREATE);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                audienceBuilderService.build(companyId, request, userId),
                "First-party audiences submitted to selected providers"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
