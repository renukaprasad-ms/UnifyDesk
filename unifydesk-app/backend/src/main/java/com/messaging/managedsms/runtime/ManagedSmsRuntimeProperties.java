package com.messaging.managedsms.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.managed-sms")
public class ManagedSmsRuntimeProperties {

  private boolean enabled = false;
  private String provider = "MSG91_SMS";
  private String providerMode = "";
  private String masterAuthKey = "";
  private String webhookSecret = "";
  private Duration complianceStaleAfter = Duration.ofHours(24);
  private boolean platformSendingEnabled = false;
  private double globalMaxPerSecond = 0.0d;
  private long newTenantHourlyCap = 0L;
  private long newTenantDailyCap = 0L;
  private BigDecimal providerBalanceReserve;
  private Duration eventReplayWindow = Duration.ofMinutes(5);
  private int webhookMaxBytes = 262144;
  private int eventPollBatchSize = 50;

  public boolean providerConfigured() {
    return enabled
        && platformSendingEnabled
        && "MSG91_SMS".equalsIgnoreCase(provider)
        && masterAuthKey != null
        && !masterAuthKey.isBlank();
  }
}
