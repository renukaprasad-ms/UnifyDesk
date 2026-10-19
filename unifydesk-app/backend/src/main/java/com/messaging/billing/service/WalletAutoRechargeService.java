package com.messaging.billing.service;

import com.messaging.billing.entity.CompanyWallet;
import com.messaging.billing.entity.WalletTransaction;
import com.messaging.billing.payment.PaymentProviderGateway;
import com.messaging.billing.payment.RazorpayProperties;
import com.messaging.billing.payment.RecurringAuthorizationService;
import com.messaging.billing.repository.CompanyWalletRepository;
import com.messaging.billing.repository.WalletTransactionRepository;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optional wallet replenishment. Never performs provider I/O under a wallet lock.
 * A network timeout pauses further attempts; durable charge intents are reconciled before credit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletAutoRechargeService {

  private static final Duration COOLDOWN = Duration.ofMinutes(5);
  private static final int MAX_FAILURES = 3;

  private final CompanyWalletRepository walletRepository;
  private final WalletTransactionRepository transactionRepository;
  private final RecurringAuthorizationService authorizationService;
  private final PaymentProviderGateway gateway;
  private final BillingNotificationService notificationService;
  private final RazorpayProperties properties;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactions;

  @Async
  public void triggerAutoRechargeIfEligibleAsync(Long companyId) {
    try {
      executeAutoRecharge(companyId);
    } catch (RuntimeException ex) {
      log.error("Wallet recharge could not proceed for company {}", companyId, ex);
    }
  }

  public void executeAutoRecharge(Long companyId) {
    if (!properties.isRecurringEnabled()) return;
    ChargeRequest request = transactions.execute(status -> claim(companyId));
    if (request == null) return;
    try {
      PaymentProviderGateway.RemoteSubscriptionPayment response =
          gateway.chargeAuthorizedSubscription(
              request.token(), request.commandId(), request.amount(),
              request.scheduledAt(), request.commandId());
      // Recheck provider state instead of trusting submission or an authorized response.
      PaymentProviderGateway.RemoteSubscriptionPayment verified =
          gateway.fetchSubscriptionPayment(request.token(), request.commandId());
      if ("SUCCESS".equalsIgnoreCase(verified.status())) {
        postVerifiedCredit(request.companyId(), request.commandId(), request.amount(), verified);
        return;
      }
      if ("FAILED".equalsIgnoreCase(verified.status())) {
        markDefinitiveFailure(companyId, request.amount());
        return;
      }
      markUnknownOutcome(companyId, request.commandId());
    } catch (RuntimeException ex) {
      markUnknownOutcome(companyId, request.commandId());
      log.warn("Wallet recharge {} needs provider reconciliation", request.commandId());
    }
  }

  /** Poll orders, including those submitted before a crash or a timeout. */
  @Scheduled(
      fixedDelayString = "${app.billing.razorpay.wallet-reconcile-interval-ms:300000}",
      initialDelayString = "${app.billing.razorpay.wallet-reconcile-initial-delay-ms:60000}")
  public void reconcileWalletDebits() {
    if (!properties.isEnabled()) return;
    record OpenCharge(String commandId, String token, Long companyId, BigDecimal amount) {}
    List<OpenCharge> open = jdbcTemplate.query(
        """
        select c.command_id, c.provider_token_id, a.company_id, c.amount
          from razorpay_recurring_charge_intents c
          join payment_provider_authorizations a
            on a.provider_subscription_id = c.provider_token_id
           and a.provider = 'RAZORPAY'
           and a.is_company_autopay = true
         where c.command_id like 'WTR-%'
           and c.created_at >= now() - interval '30 days'
           and not exists (
             select 1 from wallet_transactions t where t.reference_id = c.command_id
           )
         order by c.created_at limit 50
        """,
        (rs, index) -> new OpenCharge(
            rs.getString("command_id"), rs.getString("provider_token_id"),
            rs.getLong("company_id"), rs.getBigDecimal("amount")));
    for (OpenCharge intent : open) {
      try {
        var confirmed = gateway.fetchSubscriptionPayment(intent.token(), intent.commandId());
        if ("SUCCESS".equalsIgnoreCase(confirmed.status())) {
          postVerifiedCredit(intent.companyId(), intent.commandId(), intent.amount(), confirmed);
        }
      } catch (RuntimeException ex) {
        log.warn("Unable to reconcile wallet charge {}", intent.commandId());
      }
    }
  }

  private ChargeRequest claim(Long companyId) {
    CompanyWallet wallet = walletRepository.findByCompanyIdForUpdate(companyId).orElse(null);
    if (wallet == null || !wallet.isAutoRechargeEnabled() || wallet.isAutoRechargePaused()
        || wallet.getBalance().compareTo(wallet.getAutoRechargeThreshold()) > 0) return null;
    if (wallet.getAutoRechargeFailedAttempts() >= MAX_FAILURES) {
      wallet.setAutoRechargePaused(true);
      walletRepository.saveAndFlush(wallet);
      return null;
    }
    Instant now = Instant.now();
    if (wallet.getAutoRechargeLastAttemptAt() != null
        && wallet.getAutoRechargeLastAttemptAt().plus(COOLDOWN).isAfter(now)) return null;
    var mandate = authorizationService.getActiveCompanyAutopay(companyId);
    if (mandate == null || !"ACTIVE".equalsIgnoreCase(mandate.status())
        || !mandate.providerSubscriptionId().startsWith("token_")) return null;
    BigDecimal amount = wallet.getAutoRechargeAmount();
    if (amount == null || amount.compareTo(BigDecimal.TEN) < 0
        || amount.compareTo(new BigDecimal("15000")) > 0) {
      wallet.setAutoRechargePaused(true);
      walletRepository.saveAndFlush(wallet);
      return null;
    }
    wallet.setAutoRechargeLastAttemptAt(now);
    walletRepository.saveAndFlush(wallet);
    return new ChargeRequest(
        companyId, mandate.providerSubscriptionId(),
        "WTR-" + companyId + "-" + IdGenerator.nextId(), amount, now);
  }

  private void postVerifiedCredit(
      Long companyId, String commandId, BigDecimal amount,
      PaymentProviderGateway.RemoteSubscriptionPayment provider) {
    if (!"SUCCESS".equalsIgnoreCase(provider.status())
        || !commandId.equals(provider.paymentId())
        || provider.providerPaymentId() == null
        || !provider.providerPaymentId().startsWith("pay_")
        || provider.amount() == null || provider.amount().compareTo(amount) != 0
        || !"INR".equalsIgnoreCase(provider.currency())) {
      throw new IllegalStateException("Wallet debit has no matching captured provider payment");
    }
    Boolean credited = transactions.execute(status -> {
      CompanyWallet wallet = walletRepository.findByCompanyIdForUpdate(companyId)
          .orElseThrow(() -> new IllegalStateException("Wallet missing during reconciliation"));
      if (transactionRepository.existsByReferenceId(commandId)) return false;
      BigDecimal balance = wallet.getBalance().add(amount);
      wallet.setBalance(balance);
      wallet.setAutoRechargeFailedAttempts(0);
      wallet.setAutoRechargePaused(false);
      walletRepository.saveAndFlush(wallet);
      WalletTransaction tx = new WalletTransaction();
      tx.setCompany(wallet.getCompany());
      tx.setType("AUTO_RECHARGE");
      tx.setAmount(amount);
      tx.setBalanceAfter(balance);
      tx.setCurrency("INR");
      tx.setReferenceId(commandId);
      tx.setDescription("Verified Razorpay recurring wallet top-up");
      tx.setCreatedAt(Instant.now());
      transactionRepository.saveAndFlush(tx);
      return true;
    });
    if (Boolean.TRUE.equals(credited)) {
      var balance = walletRepository.findByCompanyId(companyId)
          .orElseThrow().getBalance();
      notificationService.sendWalletTopupSuccessEmail(companyId, amount, balance);
    }
  }

  private void markUnknownOutcome(Long companyId, String commandId) {
    transactions.executeWithoutResult(status -> {
      walletRepository.findByCompanyIdForUpdate(companyId).ifPresent(wallet -> {
        wallet.setAutoRechargePaused(true);
        walletRepository.saveAndFlush(wallet);
      });
    });
    log.warn("Paused wallet recharge for company {} while command {} is unresolved",
        companyId, commandId);
  }

  private void markDefinitiveFailure(Long companyId, BigDecimal amount) {
    Integer failures = transactions.execute(status -> {
      CompanyWallet wallet = walletRepository.findByCompanyIdForUpdate(companyId).orElse(null);
      if (wallet == null) return null;
      int count = wallet.getAutoRechargeFailedAttempts() + 1;
      wallet.setAutoRechargeFailedAttempts(count);
      if (count >= MAX_FAILURES) wallet.setAutoRechargePaused(true);
      walletRepository.saveAndFlush(wallet);
      return count;
    });
    if (failures != null) {
      notificationService.sendWalletTopupFailureEmail(
          companyId, amount, failures, failures >= MAX_FAILURES);
    }
  }

  private record ChargeRequest(
      Long companyId, String token, String commandId, BigDecimal amount, Instant scheduledAt) {}
}
