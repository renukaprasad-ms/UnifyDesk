package com.messaging.channel.meta;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.channel.meta-whatsapp")
public class MetaWhatsAppProperties {

  private boolean enabled;
  private String appId;
  private String appSecret;
  private String configurationId;
  private String graphApiVersion = "v26.0";
  private String webhookVerifyToken;
  private Duration signupSessionTtl = Duration.ofMinutes(10);
  private boolean registerPhoneNumber = true;

  public void setAppId(String appId) {
    this.appId = trimToNull(appId);
  }

  public void setAppSecret(String appSecret) {
    this.appSecret = trimToNull(appSecret);
  }

  public void setConfigurationId(String configurationId) {
    this.configurationId = trimToNull(configurationId);
  }

  public void setGraphApiVersion(String graphApiVersion) {
    this.graphApiVersion = trimToNull(graphApiVersion);
  }

  public boolean configured() {
    return enabled
        && hasText(appId)
        && hasText(appSecret)
        && hasText(configurationId)
        && hasText(graphApiVersion);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
