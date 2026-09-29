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
  private boolean coexistenceEnabled = false;
  private Duration healthCacheTtl = Duration.ofMinutes(15);
  private Duration healthyReconcileInterval = Duration.ofMinutes(30);
  private Duration degradedReconcileInterval = Duration.ofMinutes(5);
  private Duration manualReconcileMinInterval = Duration.ofSeconds(15);
  private Duration reconcilePollInterval = Duration.ofMinutes(1);

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

  public Duration safeSignupSessionTtl() {
    if (signupSessionTtl == null
        || signupSessionTtl.isNegative()
        || signupSessionTtl.isZero()
        || signupSessionTtl.compareTo(Duration.ofMinutes(10)) > 0) {
      return Duration.ofMinutes(10);
    }
    return signupSessionTtl;
  }

  public Duration safeHealthCacheTtl() {
    return positiveOrDefault(healthCacheTtl, Duration.ofMinutes(15));
  }

  public Duration safeHealthyReconcileInterval() {
    return positiveOrDefault(healthyReconcileInterval, Duration.ofMinutes(30));
  }

  public Duration safeDegradedReconcileInterval() {
    return positiveOrDefault(degradedReconcileInterval, Duration.ofMinutes(5));
  }

  public Duration safeManualReconcileMinInterval() {
    return positiveOrDefault(manualReconcileMinInterval, Duration.ofSeconds(15));
  }

  public boolean configured() {
    return enabled
        && hasText(appId)
        && hasText(appSecret)
        && hasText(configurationId)
        && hasText(graphApiVersion);
  }

  private Duration positiveOrDefault(Duration value, Duration fallback) {
    return value == null || value.isNegative() || value.isZero() ? fallback : value;
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
