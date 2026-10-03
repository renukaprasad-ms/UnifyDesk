package com.messaging.billing.payment;

import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/billing/payments")
@RequiredArgsConstructor
public class BillingPaymentController {

  private final BillingPaymentOrchestrator orchestrator;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping("/initial")
  public ResponseEntity<ApiResponse<CheckoutResponse>> initial(
      @PathVariable Long companyId, @Valid @RequestBody InitialCheckoutRequest request) {
    requireModify(companyId);
    var result =
        orchestrator.initialCheckout(
            companyId,
            parse(request.planId()),
            parse(request.planPriceId()),
            request.idempotencyKey(),
            currentUserService.currentUserId(),
            request.invoiceIdentity(),
            request.customer().toProviderCustomer(companyId));
    return created(result);
  }

  @PostMapping("/upgrade")
  public ResponseEntity<ApiResponse<CheckoutResponse>> upgrade(
      @PathVariable Long companyId, @Valid @RequestBody UpgradeCheckoutRequest request) {
    requireModify(companyId);
    return created(
        orchestrator.upgrade(
            companyId,
            parse(request.changeId()),
            request.quoteReference(),
            request.customer().toProviderCustomer(companyId)));
  }

  @PostMapping("/renewal")
  public ResponseEntity<ApiResponse<CheckoutResponse>> renewal(
      @PathVariable Long companyId, @Valid @RequestBody RenewalCheckoutRequest request) {
    requireModify(companyId);
    return created(
        orchestrator.renewal(
            companyId,
            parse(request.subscriptionId()),
            request.idempotencyKey(),
            request.invoiceIdentity(),
            request.customer().toProviderCustomer(companyId)));
  }

  private ResponseEntity<ApiResponse<CheckoutResponse>> created(
      PaymentCollectionService.CheckoutResult result) {
    CheckoutResponse body =
        new CheckoutResponse(
            result.paymentId().toString(),
            result.invoiceId().toString(),
            result.providerOrderId(),
            result.paymentSessionId(),
            result.amount(),
            result.currency(),
            result.status().name(),
            result.recurringAuthorizationSessionId(),
            result.recurringAuthorizationActionUrl());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), body, "Payment checkout prepared"));
  }

  private void requireModify(Long companyId) {
    authorizationService.requirePermission(
        currentUserService.currentUserId(),
        companyId,
        CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
  }

  private Long parse(String value) {
    try {
      return Long.valueOf(value);
    } catch (RuntimeException exception) {
      throw new BadRequestException("Invalid billing identifier");
    }
  }

  public record CustomerDetails(
      String name, @NotBlank @Email String email, @NotBlank String phone) {
    PaymentProviderGateway.Customer toProviderCustomer(Long companyId) {
      return new PaymentProviderGateway.Customer(
          "company-" + companyId, name, email.trim(), phone.trim());
    }
  }

  public record InitialCheckoutRequest(
      @NotBlank String planId,
      @NotBlank String planPriceId,
      @NotBlank String idempotencyKey,
      InvoiceIdentitySnapshot invoiceIdentity,
      @Valid CustomerDetails customer) {}

  public record UpgradeCheckoutRequest(
      @NotBlank String changeId,
      @NotBlank String quoteReference,
      @Valid CustomerDetails customer) {}

  public record RenewalCheckoutRequest(
      @NotBlank String subscriptionId,
      @NotBlank String idempotencyKey,
      InvoiceIdentitySnapshot invoiceIdentity,
      @Valid CustomerDetails customer) {}

  public record CheckoutResponse(
      String paymentId,
      String invoiceId,
      String providerOrderId,
      String paymentSessionId,
      BigDecimal amount,
      String currency,
      String status,
      String recurringAuthorizationSessionId,
      String recurringAuthorizationActionUrl) {}
}
