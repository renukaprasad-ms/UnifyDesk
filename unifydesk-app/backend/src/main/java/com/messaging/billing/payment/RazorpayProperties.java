package com.messaging.billing.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.billing.razorpay")
public class RazorpayProperties {

  private boolean enabled = true;
  /** Explicit opt-in after Razorpay confirms merchant-initiated recurring debit eligibility. */
  private boolean recurringEnabled = false;
  private String baseUrl = "https://api.razorpay.com/v1";
  private String keyId = "rzp_test_placeholder";
  private String keySecret = "rzp_test_secret_placeholder";
  private String webhookSecret = "razorpay_webhook_secret";

  private String returnUrl = "https://app.unifydesk.in/billing";
  private String notifyUrl = "https://api.unifydesk.in/api/v1/webhooks/razorpay";

  private long reconciliationIntervalMs = 300000;
  private long reconciliationInitialDelayMs = 60000;

  private int webhookMaxAttempts = 8;
  private long webhookRetryBaseSeconds = 300;
  private long webhookClaimSeconds = 120;
  private long webhookMaxAgeSeconds = 300;
  private long webhookFutureSkewSeconds = 60;

  private long dunningClaimSeconds = 300;
  private long automaticRenewalClaimSeconds = 300;
  private long automaticRenewalIntervalMs = 60000;
  private long automaticRenewalInitialDelayMs = 60000;
}
