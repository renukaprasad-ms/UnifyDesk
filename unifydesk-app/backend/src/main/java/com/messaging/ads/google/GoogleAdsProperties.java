package com.messaging.ads.google;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ads.google")
public class GoogleAdsProperties {

  private static final String DEFAULT_SCOPE =
      "openid email profile https://www.googleapis.com/auth/adwords https://www.googleapis.com/auth/datamanager https://www.googleapis.com/auth/content";

  private boolean enabled;
  private String clientId;
  private String clientSecret;
  private String redirectUri;
  private String frontendBaseUrl = "https://app.unifydesk.in";
  private String apiVersion = "v25";
  private String scope = DEFAULT_SCOPE;
  private String developerToken;
  private Duration oauthStateTtl = Duration.ofMinutes(10);
  private Duration tokenRefreshSkew = Duration.ofMinutes(2);
  private boolean backgroundSyncEnabled = true;
  private Duration backgroundSyncLockTtl = Duration.ofMinutes(20);

  public boolean configured() {
    return enabled
        && hasText(clientId)
        && hasText(clientSecret)
        && hasText(redirectUri)
        && hasText(frontendBaseUrl)
        && hasText(apiVersion)
        && hasText(scope);
  }

  public boolean hasDeveloperToken() {
    return hasText(developerToken);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
