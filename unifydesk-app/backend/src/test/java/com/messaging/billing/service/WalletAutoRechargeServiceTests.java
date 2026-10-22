package com.messaging.billing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.CompanyWallet;
import com.messaging.billing.payment.PaymentProviderGateway;
import com.messaging.billing.payment.RazorpayProperties;
import com.messaging.billing.payment.RecurringAuthorizationService;
import com.messaging.billing.repository.CompanyWalletRepository;
import com.messaging.billing.repository.WalletTransactionRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.ServiceUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WalletAutoRechargeServiceTests {
  private final CompanyWalletRepository wallets = mock(CompanyWalletRepository.class);
  private final WalletTransactionRepository ledger = mock(WalletTransactionRepository.class);
  private final PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
  private final RecurringAuthorizationService authorizations = mock(RecurringAuthorizationService.class);
  private final RazorpayProperties properties = new RazorpayProperties();
  private final CompanyWallet wallet = new CompanyWallet();
  private WalletAutoRechargeService service;

  @BeforeEach
  void setup() {
    properties.setRecurringEnabled(true);
    wallet.setAutoRechargeEnabled(true);
    when(wallets.findByCompanyIdForUpdate(1L)).thenReturn(Optional.of(wallet));
    when(wallets.findByCompanyId(1L)).thenReturn(Optional.of(wallet));
    when(authorizations.getActiveCompanyAutopay(1L)).thenReturn(
        new RecurringAuthorizationService.AuthorizationResult(
            1L, "token_test", "order_test", "ACTIVE", null, "cust_test", "card"));
    service = new WalletAutoRechargeService(wallets, ledger, authorizations, gateway,
        mock(BillingNotificationService.class), properties, mock(JdbcTemplate.class),
        new TransactionTemplate(mock(PlatformTransactionManager.class)));
  }

  @Test
  void pausedRechargeReturnsConflictWithoutResettingOrCallingProvider() {
    wallet.setAutoRechargePaused(true);
    wallet.setAutoRechargeFailedAttempts(2);
    serviceConflict();
    assertThat(wallet.isAutoRechargePaused()).isTrue();
    assertThat(wallet.getAutoRechargeFailedAttempts()).isEqualTo(2);
    verifyNoInteractions(gateway);
  }

  @Test
  void disabledAndCooldownRequestsCannotReportSuccess() {
    properties.setRecurringEnabled(false);
    serviceConflict();
    properties.setRecurringEnabled(true);
    wallet.setAutoRechargeLastAttemptAt(Instant.now());
    serviceConflict();
    verifyNoInteractions(gateway);
  }

  @Test
  void providerExceptionReturnsUnavailableAndKeepsPauseCommitted() {
    when(gateway.chargeAuthorizedSubscription(anyString(), anyString(), any(), any(), anyString()))
        .thenThrow(new IllegalStateException("provider unavailable"));
    assertThatThrownBy(() -> service.rechargeNow(1L)).isInstanceOf(ServiceUnavailableException.class);
    assertThat(wallet.isAutoRechargePaused()).isTrue();
    verifyNoInteractions(ledger);
    serviceConflict();
    verify(gateway, times(1)).chargeAuthorizedSubscription(anyString(), anyString(), any(), any(), anyString());
  }

  @Test
  void pendingPaymentDoesNotReportSuccessOrCreditWallet() {
    providerStatus("PENDING");
    assertThatThrownBy(() -> service.rechargeNow(1L)).isInstanceOf(ServiceUnavailableException.class);
    assertThat(wallet.isAutoRechargePaused()).isTrue();
    assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    verifyNoInteractions(ledger);
  }

  @Test
  void declinedPaymentReturnsConflictAndRecordsFailure() {
    providerStatus("FAILED");
    serviceConflict();
    assertThat(wallet.getAutoRechargeFailedAttempts()).isEqualTo(1);
    verifyNoInteractions(ledger);
  }

  @Test
  void capturedPaymentCreditsWalletBeforeReportingSuccess() {
    providerStatus("SUCCESS");
    assertThatCode(() -> service.rechargeNow(1L)).doesNotThrowAnyException();
    assertThat(wallet.getBalance()).isEqualByComparingTo("2000");
    verify(ledger).saveAndFlush(any());
  }

  private void serviceConflict() {
    assertThatThrownBy(() -> service.rechargeNow(1L)).isInstanceOf(ConflictException.class);
  }

  private void providerStatus(String status) {
    when(gateway.fetchSubscriptionPayment(eq("token_test"), anyString())).thenAnswer(call ->
        new PaymentProviderGateway.RemoteSubscriptionPayment(call.getArgument(1), "pay_test", status,
            new BigDecimal("2000"), "INR", Instant.now(), null, null));
  }
}
