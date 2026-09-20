package com.messaging.channel.gmail;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.channel.gmail")
public class GmailOAuthProperties {

  private static final String DEFAULT_SCOPE = "https://www.googleapis.com/auth/gmail.modify";

  private boolean enabled;
  private String clientId;
  private String clientSecret;
  private String redirectUri;
  private String frontendBaseUrl = "https://messaging.renukaprasad.in";
  private String scope = DEFAULT_SCOPE;
  private Duration oauthStateTtl = Duration.ofMinutes(10);
  private Duration tokenRefreshSkew = Duration.ofMinutes(2);
  private int initialSyncDays = 30;
  private int initialSyncMaxMessages = 200;
  private int syncMaxRangeDays = 366;
  private int syncMaxMessages = 100000;
  private int syncBatchSize = 25;
  private int syncUserQuotaUnitsPerMinute = 6000;
  private int syncMessageGetQuotaUnits = 20;
  private int syncQuotaUtilizationPercent = 80;
  private boolean quotaControlEnabled = true;
  private int quotaProjectUnitsPerMinute = 1_200_000;
  private int quotaInteractiveReservePercent = 20;
  private int quotaUserBurstUnits = 600;
  private int quotaProjectBurstUnits = 50_000;
  private int quotaBackgroundUserBurstUnits = 500;
  private int quotaBackgroundProjectBurstUnits = 40_000;
  private Duration quotaBucketTtl = Duration.ofMinutes(2);
  private Duration quotaCooldownInitial = Duration.ofSeconds(30);
  private Duration quotaCooldownMax = Duration.ofMinutes(5);
  private int syncBatchRetryMaxAttempts = 12;
  private Duration syncBatchRetryInitialBackoff = Duration.ofSeconds(2);
  private Duration syncBatchRetryMaxBackoff = Duration.ofSeconds(64);
  private Duration syncBatchRecoveryInterval = Duration.ofMinutes(1);
  private Duration syncBatchStaleAfter = Duration.ofMinutes(5);
  private int syncBatchRecoverySize = 100;
  private int workerConcurrency = 12;
  private int workerPollIntervalMs = 250;
  private int apiRetryMaxAttempts = 4;
  private Duration apiRetryInitialBackoff = Duration.ofSeconds(1);
  private Duration apiRetryMaxBackoff = Duration.ofSeconds(8);

  public boolean configured() {
    return enabled
        && hasText(clientId)
        && hasText(clientSecret)
        && hasText(redirectUri)
        && hasText(frontendBaseUrl)
        && hasText(scope);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
