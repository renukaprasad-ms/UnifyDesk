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
  private String awsRegion = "ap-southeast-2";
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
  private Duration controlRateLimitWindow = Duration.ofMinutes(1);
  private int customerDomainCreateCompanyLimit = 10;
  private int customerDomainCreateUserLimit = 5;
  private int hostedDomainCreateCompanyLimit = 10;
  private int hostedDomainCreateUserLimit = 5;
  private int domainRefreshCompanyLimit = 30;
  private int domainRefreshUserLimit = 15;
  private int domainDeleteCompanyLimit = 10;
  private int domainDeleteUserLimit = 5;
  private int senderCreateCompanyLimit = 30;
  private int senderCreateUserLimit = 15;
  private int senderDisableCompanyLimit = 30;
  private int senderDisableUserLimit = 15;

  public boolean hasCloudflareConfiguration() {
    return cloudflareZoneId != null
        && !cloudflareZoneId.isBlank()
        && cloudflareApiToken != null
        && !cloudflareApiToken.isBlank();
  }

  public boolean hasSnsTopic() {
    return snsTopicArn != null && !snsTopicArn.isBlank() && snsTopicArn.startsWith("arn:aws:sns:");
  }
}
