package com.messaging.billing.service;

import com.messaging.billing.entity.CompanyWallet;
import com.messaging.billing.entity.WalletTransaction;
import com.messaging.billing.payment.RazorpayProperties;
import com.messaging.billing.payment.PaymentProviderGateway;
import com.messaging.billing.payment.RecurringAuthorizationService;
import com.messaging.billing.repository.CompanyWalletRepository;
import com.messaging.billing.repository.WalletTransactionRepository;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletAutoRechargeService {

  private static final Duration COOLDOWN_BETWEEN_ATTEMPTS = Duration.ofMinutes(3);
  private static final int MAX_FAILED_ATTEMPTS = 3;

  private final CompanyWalletRepository walletRepository;
  private final WalletTransactionRepository transactionRepository;
  private final RecurringAuthorizationService recurringAuthorizationService;
  private final PaymentProviderGateway paymentProviderGateway;
  private final BillingNotificationService notificationService;
  private final com.messaging.billing.payment.RazorpayProperties razorpayProperties;

  /**
   * Asynchronously checks whether the wallet requires auto-recharge and executes the charge
   * against the company's active AutoPay mandate in an isolated transaction.
   */
  @Async
  public void triggerAutoRechargeIfEligibleAsync(Long companyId) {
    try {
      executeAutoRecharge(companyId);
    } catch (Exception e) {
      log.error("Error executing background wallet auto-recharge for company {}: {}", companyId, e.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void executeAutoRecharge(Long companyId) {
    if (!razorpayProperties.isEnabled()) {
      return;
    }

    CompanyWallet wallet = walletRepository.findByCompanyIdForUpdate(companyId).orElse(null);
    if (wallet == null) {
      return;
    }

    if (!wallet.isAutoRechargeEnabled() || wallet.isAutoRechargePaused()) {
      return;
    }

    if (wallet.getBalance().compareTo(wallet.getAutoRechargeThreshold()) > 0) {
      return;
    }

    if (wallet.getAutoRechargeFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
      if (!wallet.isAutoRechargePaused()) {
        wallet.setAutoRechargePaused(true);
        walletRepository.saveAndFlush(wallet);
      }
      return;
    }

    Instant now = Instant.now();
    if (wallet.getAutoRechargeLastAttemptAt() != null
        && wallet.getAutoRechargeLastAttemptAt().plus(COOLDOWN_BETWEEN_ATTEMPTS).isAfter(now)) {
      log.debug("Wallet auto-recharge in cooldown for company {}", companyId);
      return;
    }

    var mandate = recurringAuthorizationService.getActiveCompanyAutopay(companyId);
    if (mandate == null || !"ACTIVE".equalsIgnoreCase(mandate.status())) {
      log.warn("Cannot execute wallet auto-recharge: no active AutoPay mandate for company {}", companyId);
      return;
    }

    BigDecimal rechargeAmount = wallet.getAutoRechargeAmount();
    if (rechargeAmount == null || rechargeAmount.compareTo(BigDecimal.TEN) < 0) {
      rechargeAmount = new BigDecimal("2000.0000");
    }

    wallet.setAutoRechargeLastAttemptAt(now);
    wallet = walletRepository.saveAndFlush(wallet);

    String paymentId = "WTR-" + companyId + "-" + IdGenerator.nextId();
    String idempotencyKey = "autorecharge:" + companyId + ":" + (now.toEpochMilli() / (180 * 1000));

    try {
      PaymentProviderGateway.RemoteSubscriptionPayment response =
          paymentProviderGateway.chargeAuthorizedSubscription(
              mandate.providerSubscriptionId(),
              paymentId,
              rechargeAmount,
              now,
              idempotencyKey);

      if ("SUCCESS".equalsIgnoreCase(response.status())) {
        BigDecimal newBalance = wallet.getBalance().add(rechargeAmount);
        wallet.setBalance(newBalance);
        wallet.setAutoRechargeFailedAttempts(0);
        wallet.setAutoRechargePaused(false);
        walletRepository.saveAndFlush(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setCompany(wallet.getCompany());
        tx.setType("AUTO_RECHARGE");
        tx.setAmount(rechargeAmount);
        tx.setBalanceAfter(newBalance);
        tx.setCurrency(wallet.getCurrency());
        tx.setReferenceId(paymentId);
        tx.setDescription("AutoPay prepaid wallet reload");
        tx.setCreatedAt(now);
        transactionRepository.saveAndFlush(tx);

        log.info("Wallet auto-recharge succeeded for company {}: amount {}, new balance {}",
            companyId, rechargeAmount, newBalance);

        notificationService.sendWalletTopupSuccessEmail(companyId, rechargeAmount, newBalance);
      } else {
        handleChargeFailure(wallet, rechargeAmount);
      }
    } catch (Exception chargeException) {
      log.error("Wallet auto-recharge charge failed for company {}: {}", companyId, chargeException.getMessage());
      handleChargeFailure(wallet, rechargeAmount);
    }
  }

  private void handleChargeFailure(CompanyWallet wallet, BigDecimal amount) {
    int attempts = wallet.getAutoRechargeFailedAttempts() + 1;
    wallet.setAutoRechargeFailedAttempts(attempts);
    boolean paused = attempts >= MAX_FAILED_ATTEMPTS;
    if (paused) {
      wallet.setAutoRechargePaused(true);
    }
    walletRepository.saveAndFlush(wallet);

    log.warn("Wallet auto-recharge failed for company {} (attempt {} of {}, paused={})",
        wallet.getCompanyId(), attempts, MAX_FAILED_ATTEMPTS, paused);

    notificationService.sendWalletTopupFailureEmail(wallet.getCompanyId(), amount, attempts, paused);
  }
}
