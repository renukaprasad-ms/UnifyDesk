package com.messaging.billing.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.billing.cashfree")
public class CashfreeProperties {
  private boolean enabled;
  private String baseUrl = "https://sandbox.cashfree.com";
  private String clientId;
  private String clientSecret;
  private String paymentsApiVersion = "2025-01-01";
  private String subscriptionsApiVersion = "2025-01-01";
  private String returnUrl = "https://app.unifydesk.in/billing/payment-return";
  private String notifyUrl = "https://api.unifydesk.in/api/v1/webhooks/cashfree";
  private int webhookMaxAttempts = 8;
  private long webhookRetryBaseSeconds = 300L;
  private long webhookClaimSeconds = 120L;
  private long dunningClaimSeconds = 300L;
  private long automaticRenewalClaimSeconds = 300L;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getClientSecret() { return clientSecret; }
  public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
  public String getPaymentsApiVersion() { return paymentsApiVersion; }
  public void setPaymentsApiVersion(String paymentsApiVersion) { this.paymentsApiVersion = paymentsApiVersion; }
  public String getSubscriptionsApiVersion() { return subscriptionsApiVersion; }
  public void setSubscriptionsApiVersion(String subscriptionsApiVersion) { this.subscriptionsApiVersion = subscriptionsApiVersion; }
  public String getReturnUrl() { return returnUrl; }
  public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
  public String getNotifyUrl() { return notifyUrl; }
  public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
  public int getWebhookMaxAttempts() { return webhookMaxAttempts; }
  public void setWebhookMaxAttempts(int webhookMaxAttempts) { this.webhookMaxAttempts = webhookMaxAttempts; }
  public long getWebhookRetryBaseSeconds() { return webhookRetryBaseSeconds; }
  public void setWebhookRetryBaseSeconds(long webhookRetryBaseSeconds) { this.webhookRetryBaseSeconds = webhookRetryBaseSeconds; }
  public long getWebhookClaimSeconds() { return webhookClaimSeconds; }
  public void setWebhookClaimSeconds(long webhookClaimSeconds) { this.webhookClaimSeconds = webhookClaimSeconds; }
  public long getDunningClaimSeconds() { return dunningClaimSeconds; }
  public void setDunningClaimSeconds(long dunningClaimSeconds) { this.dunningClaimSeconds = dunningClaimSeconds; }
  public long getAutomaticRenewalClaimSeconds() { return automaticRenewalClaimSeconds; }
  public void setAutomaticRenewalClaimSeconds(long automaticRenewalClaimSeconds) { this.automaticRenewalClaimSeconds = automaticRenewalClaimSeconds; }
}
