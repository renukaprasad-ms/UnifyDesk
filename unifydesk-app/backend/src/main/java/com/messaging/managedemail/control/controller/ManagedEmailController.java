package com.messaging.managedemail.control.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.*;
import com.messaging.managedemail.control.service.*;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/managed-email")
@RequiredArgsConstructor
public class ManagedEmailController {

  private final ManagedEmailOverviewService overviewService;
  private final ManagedEmailDomainService domainService;
  private final ManagedEmailSenderService senderService;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/overview")
  public ResponseEntity<ApiResponse<OverviewResponse>> overview(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, overviewService.get(companyId), "Managed Email overview fetched"));
  }

  @GetMapping("/domains")
  public ResponseEntity<ApiResponse<List<DomainResponse>>> domains(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, domainService.list(companyId), "Managed Email domains fetched"));
  }

  @PostMapping("/domains/customer")
  public ResponseEntity<ApiResponse<DomainResponse>> createCustomerDomain(
      @PathVariable Long companyId, @Valid @RequestBody CustomerDomainRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    CreateResult<DomainResponse> result = domainService.createCustomer(companyId, userId, request);
    HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status)
        .body(
            ApiResponse.success(
                status.value(), result.value(), "Managed Email domain provisioning queued"));
  }

  @PostMapping("/domains/hosted")
  public ResponseEntity<ApiResponse<DomainResponse>> createHostedDomain(
      @PathVariable Long companyId, @Valid @RequestBody HostedDomainRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    CreateResult<DomainResponse> result = domainService.createHosted(companyId, userId, request);
    HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status)
        .body(
            ApiResponse.success(
                status.value(), result.value(), "Managed Email domain provisioning queued"));
  }

  @GetMapping("/domains/{domainId}")
  public ResponseEntity<ApiResponse<DomainResponse>> domain(
      @PathVariable Long companyId, @PathVariable Long domainId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, domainService.get(companyId, domainId), "Managed Email domain fetched"));
  }

  @PostMapping("/domains/{domainId}/refresh")
  public ResponseEntity<ApiResponse<DomainResponse>> refreshDomain(
      @PathVariable Long companyId, @PathVariable Long domainId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                202,
                domainService.refresh(companyId, domainId),
                "Managed Email domain refresh queued"));
  }

  @DeleteMapping("/domains/{domainId}")
  public ResponseEntity<ApiResponse<DomainResponse>> deleteDomain(
      @PathVariable Long companyId, @PathVariable Long domainId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                202,
                domainService.delete(companyId, domainId),
                "Managed Email domain cleanup queued"));
  }

  @GetMapping("/senders")
  public ResponseEntity<ApiResponse<List<SenderResponse>>> senders(@PathVariable Long companyId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, senderService.list(companyId), "Managed Email senders fetched"));
  }

  @PostMapping("/senders")
  public ResponseEntity<ApiResponse<SenderResponse>> createSender(
      @PathVariable Long companyId, @Valid @RequestBody CreateSenderRequest request) {
    Long userId = require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                senderService.create(companyId, userId, request),
                "Managed Email sender created"));
  }

  @DeleteMapping("/senders/{senderId}")
  public ResponseEntity<ApiResponse<SenderResponse>> deleteSender(
      @PathVariable Long companyId, @PathVariable Long senderId) {
    require(companyId, CompanyPermissionKeys.BROADCAST_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, senderService.disable(companyId, senderId), "Managed Email sender disabled"));
  }

  private Long require(Long companyId, String permission) {
    Long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }
}
