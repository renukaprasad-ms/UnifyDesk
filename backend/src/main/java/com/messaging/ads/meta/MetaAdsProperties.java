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
      "ads_management,business_management,pages_show_list,instagram_basic";

  private boolean enabled;
  private String appId;
  private String appSecret;
  private String redirectUri;
  private String frontendBaseUrl = "https://messaging.renukaprasad.in";
  private String graphApiVersion = "v26.0";
  private String scope = DEFAULT_SCOPE;
  private Duration oauthStateTtl = Duration.ofMinutes(10);

  public boolean configured() {
    return enabled
        && hasText(appId)
        && hasText(appSecret)
        && hasText(redirectUri)
        && hasText(frontendBaseUrl)
        && hasText(graphApiVersion)
        && hasText(scope);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
