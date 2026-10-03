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
  private String apiVersion = "2025-01-01";
  private String returnUrl = "https://app.unifydesk.in/billing/payment-return";
  private String notifyUrl = "https://api.unifydesk.in/api/v1/webhooks/cashfree";

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getClientSecret() { return clientSecret; }
  public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
  public String getApiVersion() { return apiVersion; }
  public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
  public String getReturnUrl() { return returnUrl; }
  public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
  public String getNotifyUrl() { return notifyUrl; }
  public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
}
