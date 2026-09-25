package com.messaging.ads.meta;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ads.meta")
public class MetaAdsProperties {

  private static final String DEFAULT_SCOPE =
      "ads_management,business_management,pages_show_list,pages_read_engagement,instagram_basic";

  private boolean enabled;
  private String appId;
  private String appSecret;
  private String redirectUri;
  private String frontendBaseUrl = "https://app.unifydesk.in";
  private String graphApiVersion = "v26.0";
  private String scope = DEFAULT_SCOPE;
  private Duration oauthStateTtl = Duration.ofMinutes(10);
  private String webhookVerifyToken;

  public boolean configured() {
    return enabled
        && hasText(appId)
        && hasText(appSecret)
        && hasText(redirectUri)
        && hasText(frontendBaseUrl)
        && hasText(graphApiVersion)
        && hasText(scope);
  }

  public boolean webhookConfigured() {
    return hasText(appSecret) && hasText(webhookVerifyToken);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
