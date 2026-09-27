package com.messaging.notification.platform.core.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.notification-platform")
public class NotificationPlatformProperties {

  private String frontendBaseUrl = "https://app.unifydesk.in";
  private final Core core = new Core();
  private final Retention retention = new Retention();
  private final Digest digest = new Digest();
  private final Email email = new Email();
  private final Push push = new Push();

  @Getter
  @Setter
  public static class Core {
    private final Outbox outbox = new Outbox();
    private final Fanout fanout = new Fanout();
    private final Reconciliation reconciliation = new Reconciliation();
  }

  @Getter
  @Setter
  public static class Outbox {
    private int batchSize = 100;
    private long pollIntervalMs = 750;
    private Duration lease = Duration.ofSeconds(30);
    private Duration recoveryAfter = Duration.ofMinutes(5);
    private int maxAttempts = 20;
    private Duration retryInitialBackoff = Duration.ofSeconds(1);
    private Duration retryMaxBackoff = Duration.ofMinutes(1);
    private Duration publishTimeout = Duration.ofSeconds(30);
  }

  @Getter
  @Setter
  public static class Fanout {
    private int claimSize = 10;
    private int recipientBatchSize = 500;
    private long pollIntervalMs = 750;
    private Duration lease = Duration.ofMinutes(1);
    private int maxAttempts = 20;
    private Duration retryInitialBackoff = Duration.ofSeconds(2);
    private Duration retryMaxBackoff = Duration.ofMinutes(5);
  }

  @Getter
  @Setter
  public static class Reconciliation {
    private int batchSize = 100;
    private long intervalMs = Duration.ofMinutes(15).toMillis();
  }

  @Getter
  @Setter
  public static class Retention {
    private Duration events = Duration.ofDays(90);
    private Duration notifications = Duration.ofDays(180);
    private Duration deliveryIntents = Duration.ofDays(90);
    private int cleanupBatchSize = 1000;
    private long cleanupIntervalMs = Duration.ofDays(1).toMillis();
    private long cleanupInitialDelayMs = Duration.ofMinutes(5).toMillis();
  }

  @Getter
  @Setter
  public static class Digest {
    private int threshold = 10;
    private Duration maxWait = Duration.ofHours(2);
    private Duration debounce = Duration.ofMinutes(15);
    private Duration cooldown = Duration.ofHours(4);
  }

  @Getter
  @Setter
  public static class Email {
    private boolean enabled = true;
    private int optionalRolling24hCap = 3;
  }

  @Getter
  @Setter
  public static class Push {
    private String vapidPublicKey = "";
    private String vapidPrivateKey = "";
    private int maxSubscriptionsPerUser = 10;
    private int maxPayloadBytes = 4096;
    private int maxAttempts = 4;
    private Duration inboxTtl = Duration.ofMinutes(30);
    private Duration defaultTtl = Duration.ofMinutes(15);

    public boolean configured() {
      return vapidPublicKey != null
          && !vapidPublicKey.isBlank()
          && vapidPrivateKey != null
          && !vapidPrivateKey.isBlank();
    }
  }
}
