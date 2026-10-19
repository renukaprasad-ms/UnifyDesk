package com.messaging.billing.service;

import com.messaging.billing.dto.AutoPayDetailsResponse;
import com.messaging.billing.dto.AutoPaySetupRequest;
import com.messaging.billing.entity.CompanyBillingProfile;
import com.messaging.billing.payment.PaymentProviderGateway;
import com.messaging.billing.payment.RecurringAuthorizationService;
import com.messaging.billing.repository.CompanyBillingProfileRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAutoPayService {

  private final RecurringAuthorizationService recurringAuthorizationService;
  private final CompanyBillingProfileRepository billingProfileRepository;
  private final CompanyRepository companyRepository;
  private final com.messaging.billing.payment.RazorpayProperties razorpayProperties;

  public AutoPayDetailsResponse setup(Long companyId, AutoPaySetupRequest request) {
    PaymentProviderGateway.Customer customer = resolveCustomer(companyId, request);
    if (customer.email() == null || customer.email().isBlank()
        || customer.phone() == null || !customer.phone().matches("[6-9][0-9]{9}")) {
      throw new BadRequestException(
          "A valid billing email and a real 10-digit Indian mobile number are required for AutoPay");
    }
    BigDecimal maxAmount = request != null && request.maxAmount() != null
        ? request.maxAmount()
        : new BigDecimal("15000.00");

    var result =
        recurringAuthorizationService.prepareCompanyAutopay(
            companyId, maxAmount, "INR", customer);

    log.info(
        "Prepared company AutoPay mandate for company {} with provider subscription {}",
        companyId,
        result.providerSubscriptionId());

    return new AutoPayDetailsResponse(
        String.valueOf(result.authorizationId()),
        result.status(),
        maxAmount,
        "INR",
        result.providerSubscriptionId(),
        result.subscriptionSessionId(),
        "UPI_AUTOPAY",
        result.actionUrl(),
        razorpayProperties.getKeyId(),
        result.customerId());
  }

  public AutoPayDetailsResponse get(Long companyId) {
    var result = recurringAuthorizationService.getCompanyAutopay(companyId);
    if (result == null) {
      return AutoPayDetailsResponse.notConfigured();
    }
    return new AutoPayDetailsResponse(
        String.valueOf(result.authorizationId()),
        result.status(),
        new BigDecimal("15000.00"),
        "INR",
        result.providerSubscriptionId(),
        result.subscriptionSessionId(),
        "UPI_AUTOPAY",
        result.actionUrl(),
        razorpayProperties.getKeyId(),
        result.customerId());
  }

  public AutoPayDetailsResponse verify(Long companyId, com.messaging.billing.dto.AutoPayVerifyRequest request) {
    var result = request != null && request.paymentId() != null
        ? recurringAuthorizationService.verifyCompanyAutopay(
            companyId, request.paymentId(), request.orderId(), request.signature())
        : recurringAuthorizationService.verifyAndRefreshCompanyAutopay(companyId);
    if (result == null) {
      return AutoPayDetailsResponse.notConfigured();
    }
    return new AutoPayDetailsResponse(
        String.valueOf(result.authorizationId()),
        result.status(),
        new BigDecimal("15000.00"),
        "INR",
        result.providerSubscriptionId(),
        result.subscriptionSessionId(),
        "UPI_AUTOPAY",
        result.actionUrl(),
        razorpayProperties.getKeyId(),
        result.customerId());
  }

  public boolean cancel(Long companyId) {
    boolean cancelled = recurringAuthorizationService.cancelCompanyAutopay(companyId);
    log.info("Cancelled AutoPay mandate for company {}: success={}", companyId, cancelled);
    return cancelled;
  }

  private PaymentProviderGateway.Customer resolveCustomer(
      Long companyId, AutoPaySetupRequest request) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));

    CompanyBillingProfile profile =
        billingProfileRepository.findById(companyId).orElse(null);

    String name = request != null && request.name() != null && !request.name().isBlank()
        ? request.name().trim()
        : (profile != null && profile.getLegalBusinessName() != null && !profile.getLegalBusinessName().isBlank()
            ? profile.getLegalBusinessName().trim()
            : company.getName().trim());

    String email = request != null && request.email() != null && !request.email().isBlank()
        ? request.email().trim()
        : (profile != null && profile.getBillingEmail() != null && !profile.getBillingEmail().isBlank()
            ? profile.getBillingEmail().trim()
            : (company.getBusinessEmail() != null ? company.getBusinessEmail().trim() : null));

    String phone = request != null && request.phone() != null && !request.phone().isBlank()
        ? request.phone().trim()
        : (profile != null && profile.getBillingPhone() != null && !profile.getBillingPhone().isBlank()
            ? profile.getBillingPhone().trim()
            : null);

    return new PaymentProviderGateway.Customer("company-" + companyId, name, email, phone);
  }
}
