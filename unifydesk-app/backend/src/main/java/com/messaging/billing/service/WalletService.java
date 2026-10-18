package com.messaging.billing.service;

import com.messaging.billing.dto.CompanyWalletResponse;
import com.messaging.billing.dto.ConfigureAutoRechargeRequest;
import com.messaging.billing.dto.WalletTopupOrderRequest;
import com.messaging.billing.dto.WalletTopupOrderResponse;
import com.messaging.billing.dto.WalletTransactionResponse;
import com.messaging.billing.entity.CompanyWallet;
import com.messaging.billing.entity.WalletTransaction;
import com.messaging.billing.payment.PaymentProviderGateway;
import com.messaging.billing.payment.RazorpayProperties;
import com.messaging.billing.entity.CompanyBillingProfile;
import com.messaging.billing.repository.CompanyBillingProfileRepository;
import com.messaging.billing.repository.CompanyWalletRepository;
import com.messaging.billing.repository.WalletTransactionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

  private final CompanyWalletRepository walletRepository;
  private final WalletTransactionRepository transactionRepository;
  private final CompanyRepository companyRepository;
  private final CompanyBillingProfileRepository billingProfileRepository;
  private final PaymentProviderGateway paymentProviderGateway;
  private final RazorpayProperties razorpayProperties;
  private final WalletAutoRechargeService walletAutoRechargeService;

  @Transactional
  public CompanyWallet getOrCreateWallet(Long companyId) {
    return walletRepository
        .findByCompanyId(companyId)
        .orElseGet(
            () -> {
              Company company =
                  companyRepository
                      .findById(companyId)
                      .orElseThrow(() -> new NotFoundException("Company not found: " + companyId));
              CompanyWallet wallet = new CompanyWallet();
              wallet.setNewEntity(true);
              wallet.setCompanyId(company.getId());
              wallet.setCompany(company);
              wallet.setBalance(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
              wallet.setCurrency("INR");
              wallet.setAutoRechargeEnabled(false);
              wallet.setAutoRechargeThreshold(new BigDecimal("500.0000"));
              wallet.setAutoRechargeAmount(new BigDecimal("2000.0000"));
              try {
                return walletRepository.saveAndFlush(wallet);
              } catch (Exception e) {
                log.debug("Wallet already created concurrently for company {}: {}", companyId, e.getMessage());
                return walletRepository
                    .findByCompanyId(companyId)
                    .orElseThrow(() -> new IllegalStateException("Failed to create wallet for company: " + companyId));
              }
            });
  }

  @Transactional
  public CompanyWalletResponse getWalletResponse(Long companyId) {
    CompanyWallet wallet = getOrCreateWallet(companyId);
    return toWalletResponse(wallet);
  }

  @Transactional(readOnly = true)
  public Page<WalletTransactionResponse> getTransactions(Long companyId, Pageable pageable) {
    return transactionRepository
        .findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
        .map(this::toTransactionResponse);
  }

  public WalletTopupOrderResponse createTopupOrder(
      Long companyId, WalletTopupOrderRequest request) {
    if (request.amount() == null || request.amount().compareTo(new BigDecimal("10.00")) < 0) {
      throw new BadRequestException("Minimum top-up amount is ₹10.00");
    }

    BigDecimal roundedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
    String currency = request.currency() != null && !request.currency().isBlank()
        ? request.currency().toUpperCase()
        : "INR";
    String orderId = "TOPUP-" + companyId + "-" + IdGenerator.nextId();

    boolean hasRazorpayKeys =
        razorpayProperties.isEnabled()
            && razorpayProperties.getKeyId() != null
            && !razorpayProperties.getKeyId().isBlank()
            && razorpayProperties.getKeySecret() != null
            && !razorpayProperties.getKeySecret().isBlank();

    if (hasRazorpayKeys) {
      String resolvedPhone = resolveCustomerPhone(companyId, request.customer());
      String resolvedEmail = resolveCustomerEmail(companyId, request.customer());
      String resolvedName = resolveCustomerName(companyId, request.customer());

      PaymentProviderGateway.Customer customer =
          new PaymentProviderGateway.Customer(
              "company-" + companyId,
              resolvedName,
              resolvedEmail,
              resolvedPhone);

      String returnUrl = razorpayProperties.getReturnUrl();
      String notifyUrl = razorpayProperties.getNotifyUrl();

      PaymentProviderGateway.Checkout checkout =
          paymentProviderGateway.createOrder(
              orderId,
              roundedAmount,
              currency,
              customer,
              returnUrl,
              notifyUrl,
              orderId);

      String rzpOrderId = checkout.paymentSessionId() != null && checkout.paymentSessionId().startsWith("order_")
          ? checkout.paymentSessionId()
          : null;

      return new WalletTopupOrderResponse(
          checkout.orderId(),
          checkout.paymentSessionId(),
          roundedAmount,
          currency,
          checkout.status(),
          rzpOrderId,
          razorpayProperties.getKeyId(),
          roundedAmount.multiply(BigDecimal.valueOf(100)).longValue());
    }

    // Fallback/Simulated checkout for environments without live Razorpay keys
    return new WalletTopupOrderResponse(
        orderId,
        "simulated_session_" + orderId,
        roundedAmount,
        currency,
        "ACTIVE",
        null,
        razorpayProperties.getKeyId(),
        roundedAmount.multiply(BigDecimal.valueOf(100)).longValue());
  }

  @Transactional
  public CompanyWalletResponse confirmTopup(
      Long companyId, String orderId, Long actorUserId) {
    return confirmTopup(companyId, orderId, null, actorUserId);
  }

  @Transactional
  public CompanyWalletResponse confirmTopup(
      Long companyId, String orderId, BigDecimal clientAmount, Long actorUserId) {
    if (orderId == null || orderId.isBlank()) {
      throw new BadRequestException("Order ID is required to confirm top-up");
    }

    // Pessimistic lock on company wallet
    CompanyWallet wallet =
        walletRepository
            .findByCompanyIdForUpdate(companyId)
            .orElseGet(() -> getOrCreateWallet(companyId));

    // Idempotency: verify this order reference has not already been credited
    if (transactionRepository.existsByReferenceId(orderId)) {
      log.info("Topup order {} already processed for company {}", orderId, companyId);
      return toWalletResponse(wallet);
    }

    BigDecimal amount = clientAmount != null && clientAmount.compareTo(BigDecimal.ZERO) > 0
        ? clientAmount.setScale(4, RoundingMode.HALF_UP)
        : extractAmountOrQueryGateway(orderId);

    BigDecimal updatedBalance = wallet.getBalance().add(amount);
    wallet.setBalance(updatedBalance);
    wallet = walletRepository.saveAndFlush(wallet);

    WalletTransaction tx = new WalletTransaction();
    tx.setCompany(wallet.getCompany());
    tx.setType("TOPUP");
    tx.setAmount(amount);
    tx.setBalanceAfter(updatedBalance);
    tx.setCurrency(wallet.getCurrency());
    tx.setReferenceId(orderId);
    tx.setDescription("Wallet top-up (Order: " + orderId + ")");
    tx.setActorUserId(actorUserId);
    tx.setCreatedAt(Instant.now());
    transactionRepository.saveAndFlush(tx);

    log.info("Company {} wallet credited with {} {}. New balance: {}",
        companyId, amount, wallet.getCurrency(), updatedBalance);

    return toWalletResponse(wallet);
  }

  @Transactional
  public boolean debit(
      Long companyId,
      BigDecimal amount,
      String channel,
      String referenceId,
      String description) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return true;
    }

    CompanyWallet wallet =
        walletRepository
            .findByCompanyIdForUpdate(companyId)
            .orElseGet(() -> getOrCreateWallet(companyId));

    if (wallet.getBalance().compareTo(amount) < 0) {
      log.warn("Insufficient wallet balance for company {}: required {}, available {}",
          companyId, amount, wallet.getBalance());
      if (wallet.isAutoRechargeEnabled() && !wallet.isAutoRechargePaused()) {
        log.info("Triggering auto-recharge due to zero/insufficient balance during debit for company {}", companyId);
        walletAutoRechargeService.triggerAutoRechargeIfEligibleAsync(companyId);
      }
      return false;
    }

    BigDecimal updatedBalance = wallet.getBalance().subtract(amount);
    wallet.setBalance(updatedBalance);
    wallet = walletRepository.saveAndFlush(wallet);

    WalletTransaction tx = new WalletTransaction();
    tx.setCompany(wallet.getCompany());
    tx.setType("COMMUNICATION_CHARGE");
    tx.setAmount(amount.negate());
    tx.setBalanceAfter(updatedBalance);
    tx.setCurrency(wallet.getCurrency());
    tx.setReferenceId(referenceId);
    tx.setDescription(description != null && !description.isBlank()
        ? description
        : "Communication charge (" + (channel != null ? channel : "USAGE") + ")");
    tx.setCreatedAt(Instant.now());
    transactionRepository.saveAndFlush(tx);

    if (wallet.isAutoRechargeEnabled()
        && !wallet.isAutoRechargePaused()
        && updatedBalance.compareTo(wallet.getAutoRechargeThreshold()) <= 0) {
      walletAutoRechargeService.triggerAutoRechargeIfEligibleAsync(companyId);
    }

    return true;
  }

  @Transactional
  public CompanyWalletResponse configureAutoRecharge(
      Long companyId, ConfigureAutoRechargeRequest request) {
    CompanyWallet wallet =
        walletRepository
            .findByCompanyIdForUpdate(companyId)
            .orElseGet(() -> getOrCreateWallet(companyId));

    wallet.setAutoRechargeEnabled(request.enabled());
    if (request.enabled()) {
      wallet.setAutoRechargePaused(false);
      wallet.setAutoRechargeFailedAttempts(0);
      wallet.setAutoRechargeLastAttemptAt(null);
    }
    if (request.threshold() != null && request.threshold().compareTo(BigDecimal.ZERO) > 0) {
      wallet.setAutoRechargeThreshold(request.threshold().setScale(4, RoundingMode.HALF_UP));
    }
    if (request.amount() != null && request.amount().compareTo(BigDecimal.ZERO) > 0) {
      wallet.setAutoRechargeAmount(request.amount().setScale(4, RoundingMode.HALF_UP));
    }

    wallet = walletRepository.saveAndFlush(wallet);

    if (wallet.isAutoRechargeEnabled()
        && !wallet.isAutoRechargePaused()
        && wallet.getBalance().compareTo(wallet.getAutoRechargeThreshold()) <= 0) {
      log.info("Wallet balance {} is below or equal to threshold {} after configuring auto-recharge for company {}. Triggering immediate auto-recharge.",
          wallet.getBalance(), wallet.getAutoRechargeThreshold(), companyId);
      walletAutoRechargeService.triggerAutoRechargeIfEligibleAsync(companyId);
    }

    return toWalletResponse(wallet);
  }

  @Transactional
  public CompanyWalletResponse triggerAutoRecharge(Long companyId) {
    CompanyWallet wallet =
        walletRepository
            .findByCompanyIdForUpdate(companyId)
            .orElseGet(() -> getOrCreateWallet(companyId));

    wallet.setAutoRechargeEnabled(true);
    wallet.setAutoRechargePaused(false);
    wallet.setAutoRechargeFailedAttempts(0);
    wallet.setAutoRechargeLastAttemptAt(null);
    wallet = walletRepository.saveAndFlush(wallet);

    log.info("Manual/On-demand wallet auto-recharge triggered for company {}", companyId);
    walletAutoRechargeService.triggerAutoRechargeIfEligibleAsync(companyId);
    return toWalletResponse(wallet);
  }

  private BigDecimal extractAmountOrQueryGateway(String orderId) {
    boolean hasRazorpayKeys =
        razorpayProperties.isEnabled()
            && razorpayProperties.getKeyId() != null
            && !razorpayProperties.getKeyId().isBlank()
            && razorpayProperties.getKeySecret() != null
            && !razorpayProperties.getKeySecret().isBlank();

    if (hasRazorpayKeys && orderId != null && orderId.startsWith("order_")) {
      try {
        List<PaymentProviderGateway.RemotePayment> payments =
            paymentProviderGateway.fetchOrderPayments(orderId);
        for (PaymentProviderGateway.RemotePayment p : payments) {
          if ("SUCCESS".equalsIgnoreCase(p.status())) {
            return p.amount().setScale(4, RoundingMode.HALF_UP);
          }
        }
      } catch (Exception e) {
        log.warn("Could not query Razorpay for order {}: {}", orderId, e.getMessage());
      }
    }

    // Default simulation top-up amount if not queried or testing: 1000.00
    return new BigDecimal("1000.0000");
  }

  private String resolveCustomerPhone(Long companyId, WalletTopupOrderRequest.WalletCustomerDetails customer) {
    if (customer != null && customer.phone() != null && !customer.phone().isBlank()) {
      return sanitizePhone(customer.phone());
    }
    return billingProfileRepository
        .findById(companyId)
        .map(CompanyBillingProfile::getBillingPhone)
        .filter(p -> p != null && !p.isBlank())
        .map(this::sanitizePhone)
        .orElse("9999999999");
  }

  private String sanitizePhone(String phone) {
    String digits = phone.replaceAll("[^0-9]", "");
    if (digits.length() >= 10) {
      return digits.substring(digits.length() - 10);
    }
    return "9999999999";
  }

  private String resolveCustomerEmail(Long companyId, WalletTopupOrderRequest.WalletCustomerDetails customer) {
    if (customer != null && customer.email() != null && !customer.email().isBlank()) {
      return customer.email().trim();
    }
    return billingProfileRepository
        .findById(companyId)
        .map(CompanyBillingProfile::getBillingEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElseGet(() -> companyRepository.findById(companyId).map(Company::getBusinessEmail).orElse("billing@company-" + companyId + ".local"));
  }

  private String resolveCustomerName(Long companyId, WalletTopupOrderRequest.WalletCustomerDetails customer) {
    if (customer != null && customer.name() != null && !customer.name().isBlank()) {
      return customer.name().trim();
    }
    return companyRepository.findById(companyId).map(Company::getName).orElse("Company Admin");
  }

  private CompanyWalletResponse toWalletResponse(CompanyWallet wallet) {
    return new CompanyWalletResponse(
        wallet.getCompanyId().toString(),
        wallet.getBalance(),
        wallet.getCurrency(),
        wallet.isAutoRechargeEnabled(),
        wallet.getAutoRechargeThreshold(),
        wallet.getAutoRechargeAmount(),
        wallet.getAutoRechargeFailedAttempts(),
        wallet.isAutoRechargePaused(),
        wallet.getUpdatedAt());
  }

  private WalletTransactionResponse toTransactionResponse(WalletTransaction tx) {
    return new WalletTransactionResponse(
        tx.getId().toString(),
        tx.getCompany().getId().toString(),
        tx.getType(),
        tx.getAmount(),
        tx.getBalanceAfter(),
        tx.getCurrency(),
        tx.getReferenceId(),
        tx.getDescription(),
        tx.getActorUserId(),
        tx.getCreatedAt());
  }
}
