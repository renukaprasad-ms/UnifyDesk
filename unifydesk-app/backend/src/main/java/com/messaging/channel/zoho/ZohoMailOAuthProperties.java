package com.messaging.channel.zoho;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.channel.zoho-mail")
public class ZohoMailOAuthProperties {

  private boolean enabled;
  private boolean webhookEnabled;
  private String webhookPublicBaseUrl = "https://api.unifydesk.in";
  private String clientId;
  private String clientSecret;
  private String redirectUri;
  private String frontendBaseUrl = "https://app.unifydesk.in";
  private String authorizationAccountsBaseUrl = "https://accounts.zoho.com";
  private String scope = "ZohoMail.accounts.READ,ZohoMail.folders.READ,ZohoMail.messages.ALL";
  private Duration oauthStateTtl = Duration.ofMinutes(10);
  private Duration tokenRefreshSkew = Duration.ofMinutes(2);
  private Duration pollInterval = Duration.ofMinutes(2);
  private Duration pollInitialDelay = Duration.ofMinutes(1);
  private int pollPageSize = 100;
  private int pollMaxPagesPerFolder = 50;
  private int syncMaxMessages = 10000;
  private int syncMaxRangeDays = 366;
  private int workerConcurrency = 4;
  private int workerPollIntervalMs = 500;
  private Duration staleJobAfter = Duration.ofMinutes(10);
  private int apiRetryMaxAttempts = 4;
  private Duration apiRetryInitialBackoff = Duration.ofSeconds(1);
  private Duration apiRetryMaxBackoff = Duration.ofSeconds(20);

  public boolean configured() {
    return enabled
        && hasText(clientId)
        && hasText(clientSecret)
        && hasText(redirectUri)
        && hasText(frontendBaseUrl)
        && hasText(authorizationAccountsBaseUrl)
        && hasText(scope);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
