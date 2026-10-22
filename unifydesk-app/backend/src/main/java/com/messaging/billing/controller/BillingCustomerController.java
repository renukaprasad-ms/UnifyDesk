package com.messaging.billing.controller;

import com.messaging.billing.dto.BillingUsageSummaryResponse;
import com.messaging.billing.dto.CompanyBillingProfileDto;
import com.messaging.billing.dto.CompanyWalletResponse;
import com.messaging.billing.dto.ConfigureAutoRechargeRequest;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.dto.WalletTopupConfirmRequest;
import com.messaging.billing.dto.WalletTopupOrderRequest;
import com.messaging.billing.dto.WalletTopupOrderResponse;
import com.messaging.billing.dto.WalletTransactionResponse;
import com.messaging.billing.service.BillingUsageService;
import com.messaging.billing.service.CompanyBillingProfileService;
import com.messaging.billing.service.InvoiceService;
import com.messaging.billing.service.WalletService;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/billing")
@RequiredArgsConstructor
public class BillingCustomerController {

  private final WalletService walletService;
  private final CompanyBillingProfileService profileService;
  private final InvoiceService invoiceService;
  private final BillingUsageService usageService;
  private final CompanyAuthorizationService companyAuthorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping("/wallet")
  public ResponseEntity<ApiResponse<CompanyWalletResponse>> getWallet(
      @PathVariable Long companyId) {
    requireBalanceRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            walletService.getWalletResponse(companyId),
            "Wallet fetched"));
  }

  @GetMapping("/wallet/transactions")
  public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getTransactions(
      @PathVariable Long companyId,
      @PageableDefault(size = 20) Pageable pageable) {
    requireBalanceRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            walletService.getTransactions(companyId, pageable),
            "Wallet transactions fetched"));
  }

  @PostMapping("/wallet/topup-order")
  public ResponseEntity<ApiResponse<WalletTopupOrderResponse>> createTopupOrder(
      @PathVariable Long companyId,
      @Valid @RequestBody WalletTopupOrderRequest request) {
    requireBalanceAdd(companyId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                walletService.createTopupOrder(companyId, request),
                "Top-up order prepared"));
  }

  @PostMapping("/wallet/topup-confirm")
  public ResponseEntity<ApiResponse<CompanyWalletResponse>> confirmTopup(
      @PathVariable Long companyId,
      @Valid @RequestBody WalletTopupConfirmRequest request) {
    requireBalanceAdd(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            walletService.confirmTopup(
                companyId, request.orderId(), request.amount(), currentUserService.currentUserId()),
            "Wallet topped up successfully"));
  }

  @PutMapping("/wallet/auto-recharge")
  public ResponseEntity<ApiResponse<CompanyWalletResponse>> configureAutoRecharge(
      @PathVariable Long companyId,
      @Valid @RequestBody ConfigureAutoRechargeRequest request) {
    requireBalanceAdd(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            walletService.configureAutoRecharge(companyId, request),
            "Auto-recharge configuration updated"));
  }

  @PostMapping("/wallet/auto-recharge/trigger")
  public ResponseEntity<ApiResponse<CompanyWalletResponse>> triggerAutoRecharge(
      @PathVariable Long companyId) {
    requireBalanceAdd(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            walletService.triggerAutoRecharge(companyId),
            "Wallet auto-recharge completed successfully"));
  }

  @GetMapping("/profile")
  public ResponseEntity<ApiResponse<CompanyBillingProfileDto>> getProfile(
      @PathVariable Long companyId) {
    requireBillingRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            profileService.getProfile(companyId),
            "Billing profile fetched"));
  }

  @PutMapping("/profile")
  public ResponseEntity<ApiResponse<CompanyBillingProfileDto>> updateProfile(
      @PathVariable Long companyId,
      @Valid @RequestBody CompanyBillingProfileDto dto) {
    requireProfileModify(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            profileService.updateProfile(companyId, dto),
            "Billing profile updated"));
  }

  @GetMapping("/invoices")
  public ResponseEntity<ApiResponse<List<InvoiceResponse>>> getInvoices(
      @PathVariable Long companyId) {
    requireInvoiceRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            invoiceService.invoices(companyId),
            "Company invoices fetched"));
  }

  @GetMapping("/usage")
  public ResponseEntity<ApiResponse<BillingUsageSummaryResponse>> getUsage(
      @PathVariable Long companyId) {
    requireBillingRead(companyId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            usageService.getUsageSummary(companyId),
            "Usage summary fetched"));
  }

  private void requireBillingRead(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_READ);
  }

  private void requireBalanceRead(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_BALANCE_READ);
  }

  private void requireBalanceAdd(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_BALANCE_ADD);
  }

  private void requireInvoiceRead(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_INVOICE_READ);
  }

  private void requireProfileModify(Long companyId) {
    companyAuthorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
  }
}
