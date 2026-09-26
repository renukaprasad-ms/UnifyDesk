package com.messaging.managedemail.runtime;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.managed-email.runtime")
public class ManagedEmailRuntimeProperties {

  private boolean sendingEnabled = false;
  private String region = "";
  private String snsTopicArn = "";

  private double accountSafetyMaxPerSecond = 0.0;
  private double companyMaxPerSecond = 0.0;
  private double planMaxPerSecond = 0.0;
  private double runMaxPerSecond = 0.0;
  private double newTenantMaxPerSecond = 0.0;

  private long accountDailySafetyCap = 0;
  private long newTenantHourlyCap = 0;
  private long newTenantDailyCap = 0;
  private long establishedHourlyCap = 0;
  private long establishedDailyCap = 0;
  private long campaignSizeCap = 0;
  private int providerSnapshotMaxAgeSeconds = 300;

  private int providerEffectPollBatchSize = 25;
  private int providerEffectStaleAfterSeconds = 120;
  private int providerEffectMaxAttempts = 8;
  private long providerEffectRetryInitialSeconds = 2;
  private long providerEffectRetryMaxSeconds = 900;

  private int snsReplayWindowSeconds = 300;
  private int webhookMaxBytes = 262144;
  private int webhookMaxJsonDepth = 32;
  private int signingCertificateMaxBytes = 65536;

  private int staleReservationMinutes = 30;
  private int reputationWindowHours = 24;
  private long minimumReputationSampleSize = 0;

  private BigDecimal warningHardBounceRate;
  private BigDecimal warningComplaintRate;
  private BigDecimal throttleHardBounceRate;
  private BigDecimal throttleComplaintRate;
  private BigDecimal suspensionHardBounceRate;
  private BigDecimal suspensionComplaintRate;
  private BigDecimal throttleRateMultiplier;

  public boolean reputationPolicyConfigured() {
    return minimumReputationSampleSize > 0
        && warningHardBounceRate != null
        && warningComplaintRate != null
        && throttleHardBounceRate != null
        && throttleComplaintRate != null
        && suspensionHardBounceRate != null
        && suspensionComplaintRate != null
        && throttleRateMultiplier != null
        && throttleRateMultiplier.signum() > 0
        && throttleRateMultiplier.compareTo(BigDecimal.ONE) <= 0;
  }
}
