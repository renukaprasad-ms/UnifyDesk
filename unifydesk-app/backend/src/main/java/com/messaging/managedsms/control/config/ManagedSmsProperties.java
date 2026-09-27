package com.messaging.managedsms.control.config;

import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProviderMode;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.managed-sms")
public class ManagedSmsProperties {
  private boolean enabled;
  private String provider = "MSG91_SMS";
  private ProviderMode providerMode = ProviderMode.CHILD_PER_COMPANY;
  private String masterAuthKey = "";
  private String webhookSecret = "";
  private Duration complianceStaleAfter = Duration.ofDays(7);
  private Duration provisioningPollInterval = Duration.ofSeconds(5);
  private Duration provisioningStaleAfter = Duration.ofMinutes(5);
  private Duration complianceRefreshInterval = Duration.ofMinutes(5);
  private boolean platformSendingEnabled;
  private double globalMaxPerSecond = 20.0;
  private long newTenantHourlyCap = 100;
  private long newTenantDailyCap = 500;
  private double providerBalanceReserve = 100.0;
  private Duration eventReplayWindow = Duration.ofMinutes(10);

  public boolean hasMasterAuthKey() {
    return masterAuthKey != null && !masterAuthKey.isBlank();
  }

  public boolean hasWebhookSecret() {
    return webhookSecret != null && !webhookSecret.isBlank();
  }
}
