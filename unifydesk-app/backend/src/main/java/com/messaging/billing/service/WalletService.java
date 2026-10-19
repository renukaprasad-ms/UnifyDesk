package com.messaging.billing.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.messaging.common.validation.PhoneValidator;

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
import com.messaging.common.exception.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
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
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

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
    if (!"INR".equals(currency)) throw new BadRequestException("Only INR wallet top-ups are supported");
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

      jdbcTemplate.update(
          "insert into wallet_topup_intents "
              + "(id, company_id, internal_order_id, amount, currency, status) "
              + "values (?, ?, ?, ?, ?, 'CREATE_PENDING')",
          IdGenerator.nextId(), companyId, orderId, roundedAmount, currency);
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

      if (rzpOrderId == null) throw new ConflictException("Razorpay checkout order was not created");
      int bound = jdbcTemplate.update(
          "update wallet_topup_intents set provider_order_id=?, status='CREATED' "
              + "where company_id=? and internal_order_id=? and status='CREATE_PENDING'",
          rzpOrderId, companyId, orderId);
      if (bound != 1) throw new ConflictException("Wallet checkout order binding conflicted");
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

    throw new ConflictException("Razorpay is not configured; wallet top-up is unavailable");
  }

  public CompanyWalletResponse confirmTopup(
      Long companyId, String orderId, Long actorUserId) {
    return confirmTopup(companyId, orderId, null, actorUserId);
  }

  /**
   * Verify a durable company-owned order against Razorpay before entering the wallet transaction.
   * Never accept the amount supplied by the browser or a webhook as financial authority.
   */
  public CompanyWalletResponse confirmTopup(
      Long companyId, String orderId, BigDecimal clientAmount, Long actorUserId) {
    if (orderId == null || orderId.isBlank()) {
      throw new BadRequestException("Wallet top-up order is required");
    }
    List<TopupIntent> intents = jdbcTemplate.query(
        "select internal_order_id, provider_order_id, amount, currency, status "
            + "from wallet_topup_intents where company_id=? "
            + "and (internal_order_id=? or provider_order_id=?)",
        (rs, index) -> new TopupIntent(
            rs.getString("internal_order_id"), rs.getString("provider_order_id"),
            rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("status")),
        companyId, orderId, orderId);
    if (intents.size() != 1) throw new BadRequestException("Unknown company top-up order");
    TopupIntent intent = intents.getFirst();
    if ("CREDITED".equals(intent.status())) return getWalletResponse(companyId);
    if (clientAmount != null && clientAmount.compareTo(intent.amount()) != 0) {
      throw new BadRequestException("Wallet top-up amount does not match the order");
    }

    PaymentProviderGateway.Checkout order = paymentProviderGateway.fetchOrder(intent.providerOrderId());
    if (!intent.providerOrderId().equals(order.paymentSessionId())
        || !"paid".equalsIgnoreCase(order.status())) {
      throw new ConflictException("Razorpay has not confirmed the wallet top-up order as paid");
    }
    List<PaymentProviderGateway.RemotePayment> payments =
        paymentProviderGateway.fetchOrderPayments(intent.providerOrderId());
    List<PaymentProviderGateway.RemotePayment> captured = payments.stream()
        .filter(p -> "SUCCESS".equalsIgnoreCase(p.status()))
        .toList();
    if (captured.size() != 1) {
      throw new ConflictException("Wallet top-up requires exactly one captured Razorpay payment");
    }
    PaymentProviderGateway.RemotePayment capturedPayment = captured.getFirst();
    if (capturedPayment.providerPaymentId() == null
        || !capturedPayment.providerPaymentId().startsWith("pay_")
        || capturedPayment.amount() == null
        || capturedPayment.amount().compareTo(intent.amount()) != 0
        || !intent.currency().equalsIgnoreCase(capturedPayment.currency())) {
      throw new ConflictException("Captured Razorpay payment does not match this wallet order");
    }

    return transactionTemplate.execute(transaction -> {
      List<TopupIntent> locked = jdbcTemplate.query(
          "select internal_order_id, provider_order_id, amount, currency, status "
              + "from wallet_topup_intents where company_id=? and internal_order_id=? for update",
          (rs, index) -> new TopupIntent(
              rs.getString("internal_order_id"), rs.getString("provider_order_id"),
              rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("status")),
          companyId, intent.internalOrderId());
      if (locked.size() != 1) throw new ConflictException("Wallet top-up intent was removed");
      TopupIntent current = locked.getFirst();
      if ("CREDITED".equals(current.status())) return getWalletResponse(companyId);
      if (!current.providerOrderId().equals(intent.providerOrderId())
          || current.amount().compareTo(intent.amount()) != 0
          || !current.currency().equalsIgnoreCase(intent.currency())) {
        throw new ConflictException("Wallet top-up intent changed during verification");
      }
      CompanyWallet wallet = walletRepository.findByCompanyIdForUpdate(companyId)
          .orElseGet(() -> getOrCreateWallet(companyId));
      if (transactionRepository.existsByReferenceId(intent.internalOrderId())) {
        throw new ConflictException("Wallet ledger already contains this top-up");
      }
      BigDecimal newBalance = wallet.getBalance().add(intent.amount());
      wallet.setBalance(newBalance);
      walletRepository.saveAndFlush(wallet);
      WalletTransaction ledger = new WalletTransaction();
      ledger.setCompany(wallet.getCompany());
      ledger.setType("TOPUP");
      ledger.setAmount(intent.amount());
      ledger.setBalanceAfter(newBalance);
      ledger.setCurrency(intent.currency());
      ledger.setReferenceId(intent.internalOrderId());
      ledger.setDescription("Verified wallet top-up");
      ledger.setActorUserId(actorUserId);
      ledger.setCreatedAt(Instant.now());
      transactionRepository.saveAndFlush(ledger);
      jdbcTemplate.update(
          "update wallet_topup_intents set status='CREDITED', provider_payment_id=?, "
              + "credited_at=now() where company_id=? and internal_order_id=? and status='CREATED'",
          capturedPayment.providerPaymentId(), companyId, intent.internalOrderId());
      return toWalletResponse(wallet);
    });
  }

  private record TopupIntent(String internalOrderId, String providerOrderId,
      BigDecimal amount, String currency, String status) {}

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

  /** Recover paid wallet checkouts when browser confirmation or webhooks are lost. */
  @org.springframework.scheduling.annotation.Scheduled(
      fixedDelayString = "${app.billing.razorpay.wallet-topup-reconcile-interval-ms:300000}",
      initialDelayString = "${app.billing.razorpay.wallet-topup-reconcile-initial-delay-ms:60000}")
  public void reconcileUncreditedTopups() {
    if (!razorpayProperties.isEnabled()) return;
    var pending = jdbcTemplate.queryForList(
        "select company_id, provider_order_id from wallet_topup_intents "
            + "where status='CREATED' order by created_at limit 50");
    for (var intent : pending) {
      try {
        confirmTopup(
            ((Number) intent.get("company_id")).longValue(),
            (String) intent.get("provider_order_id"), null, null);
      } catch (RuntimeException ex) {
        // Unpaid or temporarily unverifiable checkouts remain pending without credit.
        log.debug("Wallet checkout reconciliation pending: {}", ex.getClass().getSimpleName());
      }
    }
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
    if (!new PhoneValidator().isValid(phone, null)) {
      throw new BadRequestException("Enter a valid billing phone number with country code");
    }
    try {
      PhoneNumberUtil phones = PhoneNumberUtil.getInstance();
      return phones.format(phones.parse(phone, "IN"), PhoneNumberUtil.PhoneNumberFormat.E164);
    } catch (NumberParseException exception) {
      throw new BadRequestException("Enter a valid billing phone number with country code");
    }
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
