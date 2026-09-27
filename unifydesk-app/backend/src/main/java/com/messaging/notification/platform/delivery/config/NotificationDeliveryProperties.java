package com.messaging.notification.platform.delivery.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification-platform")
public class NotificationDeliveryProperties {

  private String frontendBaseUrl = "https://app.unifydesk.in";
  private Delivery delivery = new Delivery();
  private Digest digest = new Digest();
  private Email email = new Email();
  private Push push = new Push();
  private Retention retention = new Retention();

  @Getter
  @Setter
  public static class Delivery {
    private boolean enabled = true;
    private int claimBatchSize = 100;
    private Duration claimLease = Duration.ofMinutes(2);
    private Duration pollDelay = Duration.ofSeconds(1);
    private int emailMaxAttempts = 5;
  }

  @Getter
  @Setter
  public static class Digest {
    private boolean enabled = true;
    private int aggregationBatchSize = 250;
    private int triggerCount = 10;
    private Duration maxWait = Duration.ofHours(2);
    private Duration debounce = Duration.ofMinutes(15);
    private Duration cooldown = Duration.ofHours(4);
    private int optionalEmailRollingCap = 3;
    private Duration rollingWindow = Duration.ofHours(24);
    private Duration pollDelay = Duration.ofMinutes(1);
  }

  @Getter
  @Setter
  public static class Email {
    private boolean enabled = true;
    private Duration disabledRetryDelay = Duration.ofMinutes(5);
  }

  @Getter
  @Setter
  public static class Push {
    private String vapidPublicKey;
    private String vapidPrivateKey;
    private String vapidSubject = "mailto:support@unifydesk.in";
    private int maxActiveSubscriptions = 10;
    private int targetPayloadBytes = 2048;
    private int maxPayloadBytes = 4096;
    private int maxAttempts = 4;
    private Duration inboxTtl = Duration.ofMinutes(30);
    private Duration defaultTtl = Duration.ofMinutes(15);
    private Duration cleanupDelay = Duration.ofHours(1);

    public boolean configured() {
      return hasText(vapidPublicKey) && hasText(vapidPrivateKey) && hasText(vapidSubject);
    }

    private boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }

  @Getter
  @Setter
  public static class Retention {
    private Duration deliveryIntents = Duration.ofDays(90);
    private Duration deliveryAttempts = Duration.ofDays(30);
    private Duration exceptionalDeliveryAttempts = Duration.ofDays(90);
    private Duration pushAttempts = Duration.ofDays(30);
    private Duration exceptionalPushAttempts = Duration.ofDays(90);
    private Duration digestBuckets = Duration.ofDays(30);
    private Duration pushSubscriptions = Duration.ofDays(30);
    private int cleanupBatchSize = 1000;
    private long cleanupIntervalMs = Duration.ofDays(1).toMillis();
    private long cleanupInitialDelayMs = Duration.ofMinutes(5).toMillis();
  }
}
