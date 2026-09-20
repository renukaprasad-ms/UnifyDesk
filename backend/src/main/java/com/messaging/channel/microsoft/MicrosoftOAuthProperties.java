package com.messaging.channel.microsoft;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.channel.microsoft")
public class MicrosoftOAuthProperties {

  private boolean enabled;
  private String tenant = "common";
  private String clientId;
  private String clientSecret;
  private String redirectUri;
  private String frontendBaseUrl = "https://messaging.renukaprasad.in";
  private String webhookNotificationUrl;
  private String scope =
      "openid profile email offline_access User.Read Mail.ReadWrite Mail.Send";
  private Duration oauthStateTtl = Duration.ofMinutes(10);
  private Duration tokenRefreshSkew = Duration.ofMinutes(2);
  private Duration liveInitialLookback = Duration.ofMinutes(5);
  private int backfillMaxMessages = 10000;
  private int backfillMaxRangeDays = 365;
  private int backfillWorkerConcurrency = 4;
  private int backfillPollIntervalMs = 500;
  private Duration subscriptionLifetime = Duration.ofDays(6).plusHours(23);
  private Duration subscriptionRenewBefore = Duration.ofHours(24);
  private int deltaPageSize = 50;
  private int workerConcurrency = 8;
  private int workerPollIntervalMs = 500;
  private int apiRetryMaxAttempts = 4;
  private Duration apiRetryInitialBackoff = Duration.ofSeconds(1);
  private Duration apiRetryMaxBackoff = Duration.ofSeconds(30);

  public boolean configured() {
    return enabled
        && hasText(tenant)
        && hasText(clientId)
        && hasText(clientSecret)
        && hasText(redirectUri)
        && hasText(frontendBaseUrl)
        && hasText(scope);
  }

  public boolean webhookConfigured() {
    return configured() && hasText(webhookNotificationUrl);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
