package com.messaging.managedemail.control.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.managed-email")
public class ManagedEmailProperties {

  private boolean enabled;
  private boolean platformSendingEnabled;
  private String awsRegion = "ap-south-1";
  private String hostedBaseDomain = "unifydesk.in";
  private String snsTopicArn = "";
  private String cloudflareZoneId = "";
  private String cloudflareApiToken = "";
  private Duration provisioningPollInterval = Duration.ofSeconds(2);
  private Duration provisioningStaleAfter = Duration.ofMinutes(5);
  private Duration verificationRefreshInterval = Duration.ofMinutes(5);
  private double accountRateSafetyCap = 1000.0;
  private long newTenantHourlyCap = 100;
  private long newTenantDailyCap = 500;
  private Duration eventReplayWindow = Duration.ofMinutes(10);

  public boolean hasCloudflareConfiguration() {
    return cloudflareZoneId != null
        && !cloudflareZoneId.isBlank()
        && cloudflareApiToken != null
        && !cloudflareApiToken.isBlank();
  }

  public boolean hasSnsTopic() {
    return snsTopicArn != null && !snsTopicArn.isBlank();
  }
}
