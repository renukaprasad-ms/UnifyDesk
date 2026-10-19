package com.messaging.billing.payment;

import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.realtime.service.RealtimeService;
import com.messaging.security.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
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
  private final RazorpayProperties razorpayProperties;
  private final RazorpayWebhookVerifier razorpayVerifier;
  private final PaymentConfirmationService confirmationService;
  private final PaymentRepository paymentRepository;
  private final RealtimeService realtimeService;

  @PostMapping("/checkout")
  public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
      @PathVariable Long companyId, @Valid @RequestBody UnifiedCheckoutRequest request) {
    requireModify(companyId);
    PaymentCollectionService.CheckoutResult result;
    CustomerDetails cust = request.customer() != null
        ? request.customer()
        : new CustomerDetails("Company Admin", "billing@company.com", "9999999999");

    if (request.planId() != null && request.planPriceId() != null) {
      result = orchestrator.initialCheckout(
          companyId,
          parse(request.planId()),
          parse(request.planPriceId()),
          request.idempotencyKey() != null ? request.idempotencyKey() : "order:" + System.currentTimeMillis(),
          currentUserService.currentUserId(),
          request.invoiceIdentity(),
          cust.toProviderCustomer(companyId));
    } else if (request.changeId() != null && request.quoteReference() != null) {
      result = orchestrator.upgrade(
          companyId,
          parse(request.changeId()),
          request.quoteReference(),
          cust.toProviderCustomer(companyId));
    } else if (request.subscriptionId() != null) {
      result = orchestrator.renewal(
          companyId,
          parse(request.subscriptionId()),
          request.idempotencyKey() != null ? request.idempotencyKey() : "renewal:" + System.currentTimeMillis(),
          request.invoiceIdentity(),
          cust.toProviderCustomer(companyId));
    } else {
      throw new BadRequestException("Invalid checkout parameters: plan, upgrade, or renewal identity is required");
    }

    return created(result, cust);
  }

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
    return created(result, request.customer());
  }

  @PostMapping("/upgrade")
  public ResponseEntity<ApiResponse<CheckoutResponse>> upgrade(
      @PathVariable Long companyId, @Valid @RequestBody UpgradeCheckoutRequest request) {
    requireModify(companyId);
    var result =
        orchestrator.upgrade(
            companyId,
            parse(request.changeId()),
            request.quoteReference(),
            request.customer().toProviderCustomer(companyId));
    return created(result, request.customer());
  }

  @PostMapping("/renewal")
  public ResponseEntity<ApiResponse<CheckoutResponse>> renewal(
      @PathVariable Long companyId, @Valid @RequestBody RenewalCheckoutRequest request) {
    requireModify(companyId);
    var result =
        orchestrator.renewal(
            companyId,
            parse(request.subscriptionId()),
            request.idempotencyKey(),
            request.invoiceIdentity(),
            request.customer().toProviderCustomer(companyId));
    return created(result, request.customer());
  }

  @PostMapping("/verify")
  public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verify(
      @PathVariable Long companyId, @Valid @RequestBody PaymentVerifyRequest request) {
    requireModify(companyId);

    boolean valid = razorpayVerifier.verifyPaymentSignature(
        request.razorpayOrderId(),
        request.razorpayPaymentId(),
        request.razorpaySignature());

    if (!valid) {
      throw new BadRequestException("Invalid payment signature verification failed");
    }

    Payment payment = null;
    if (request.paymentId() != null && !request.paymentId().isBlank()) {
      try {
        Long numericId = Long.valueOf(request.paymentId().trim());
        payment = paymentRepository.findById(numericId).orElse(null);
      } catch (NumberFormatException ignored) {
      }
    }
    if (payment == null && request.paymentId() != null && !request.paymentId().isBlank()) {
      payment = paymentRepository.findByProviderAndProviderOrderId("RAZORPAY", request.paymentId().trim())
          .or(() -> paymentRepository.findByProviderOrderIdOrCheckoutSessionId(request.paymentId().trim()))
          .orElse(null);
    }
    if (payment == null && request.razorpayOrderId() != null && !request.razorpayOrderId().isBlank()) {
      payment = paymentRepository.findByProviderAndProviderOrderId("RAZORPAY", request.razorpayOrderId().trim())
          .or(() -> paymentRepository.findByProviderOrderIdOrCheckoutSessionId(request.razorpayOrderId().trim()))
          .orElse(null);
    }
    if (payment == null) {
      throw new BadRequestException("Payment record not found for verification");
    }

    confirmationService.confirmSuccessByPaymentId(
        payment.getId(),
        request.razorpayPaymentId(),
        payment.getAmount(),
        payment.getCurrency(),
        Instant.now());

    try {
      realtimeService.sendToCompany(
          companyId,
          "billing.payment.updated",
          Map.of(
              "companyId", companyId,
              "paymentId", payment.getId(),
              "orderId", request.razorpayOrderId(),
              "providerPaymentId", request.razorpayPaymentId(),
              "status", "SUCCEEDED",
              "amount", payment.getAmount()));
    } catch (Exception ignored) {
    }

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            new PaymentVerifyResponse(true, "SUCCEEDED", payment.getId().toString()),
            "Payment verified and confirmed successfully"));
  }

  private ResponseEntity<ApiResponse<CheckoutResponse>> created(
      PaymentCollectionService.CheckoutResult result, CustomerDetails customer) {
    long paise = result.amount() != null
        ? result.amount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValue()
        : 0L;

    // In Razorpay, the order ID (e.g. order_xxx) is returned in paymentSessionId
    String razorpayOrderId = result.paymentSessionId() != null && !result.paymentSessionId().isBlank()
        ? result.paymentSessionId()
        : result.providerOrderId();

    CheckoutResponse body =
        new CheckoutResponse(
            result.paymentId().toString(),
            result.invoiceId().toString(),
            result.providerOrderId(),
            razorpayOrderId,
            razorpayProperties.getKeyId(),
            result.amount(),
            paise,
            result.currency(),
            result.status().name(),
            customer != null ? customer.name() : "",
            customer != null ? customer.email() : "",
            customer != null ? customer.phone() : "",
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

  public record UnifiedCheckoutRequest(
      String planId,
      String planPriceId,
      String changeId,
      String quoteReference,
      String subscriptionId,
      String idempotencyKey,
      InvoiceIdentitySnapshot invoiceIdentity,
      CustomerDetails customer) {}

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

  public record PaymentVerifyRequest(
      @NotBlank String paymentId,
      @NotBlank String razorpayOrderId,
      @NotBlank String razorpayPaymentId,
      @NotBlank String razorpaySignature) {}

  public record PaymentVerifyResponse(
      boolean verified,
      String status,
      String paymentId) {}

  public record CheckoutResponse(
      String paymentId,
      String invoiceId,
      String providerOrderId,
      String razorpayOrderId,
      String keyId,
      BigDecimal amount,
      long amountInPaise,
      String currency,
      String status,
      String customerName,
      String customerEmail,
      String customerPhone,
      String recurringAuthorizationSessionId,
      String recurringAuthorizationActionUrl) {}
}
