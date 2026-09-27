package com.messaging.notification.platform.delivery.push;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StandardWebPushGateway implements WebPushGateway {

  private final NotificationDeliveryProperties properties;

  @Override
  public PushSendResult send(
      String endpoint, String p256dh, String auth, String payload, Duration ttl, String urgency) {
    if (!properties.getPush().configured()) {
      return new PushSendResult(
          PushSendResult.Outcome.PERMANENT_FAILURE, null, null, "VAPID_NOT_CONFIGURED");
    }
    try {
      ensureProvider();
      PushService pushService =
          new PushService(
              properties.getPush().getVapidPublicKey(),
              properties.getPush().getVapidPrivateKey(),
              properties.getPush().getVapidSubject());
      Notification notification =
          Notification.builder()
              .endpoint(endpoint)
              .userPublicKey(p256dh)
              .userAuth(auth)
              .payload(payload)
              .ttl(Math.toIntExact(Math.max(0, ttl.toSeconds())))
              .urgency(parseUrgency(urgency))
              .build();
      HttpResponse response = pushService.send(notification);
      int status = response.getStatusLine().getStatusCode();
      return classifyStatus(status, parseRetryAfter(response.getFirstHeader("Retry-After")));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new PushSendResult(
          PushSendResult.Outcome.TRANSIENT_FAILURE, null, null, "PUSH_INTERRUPTED");
    } catch (ExecutionException | GeneralSecurityException | java.io.IOException exception) {
      return new PushSendResult(
          PushSendResult.Outcome.TRANSIENT_FAILURE, null, null, "PUSH_NETWORK_OR_CRYPTO_FAILURE");
    } catch (RuntimeException exception) {
      return new PushSendResult(
          PushSendResult.Outcome.PERMANENT_FAILURE, null, null, "PUSH_CONFIGURATION_INVALID");
    } catch (org.jose4j.lang.JoseException exception) {
      return new PushSendResult(
          PushSendResult.Outcome.PERMANENT_FAILURE, null, null, "PUSH_VAPID_SIGNING_FAILED");
    }
  }

  PushSendResult classifyStatus(int status, Duration retryAfter) {
    if (status >= 200 && status < 300) {
      return new PushSendResult(PushSendResult.Outcome.ACCEPTED, status, null, null);
    }
    if (status == 404 || status == 410) {
      return new PushSendResult(
          PushSendResult.Outcome.EXPIRED, status, null, "PUSH_SUBSCRIPTION_EXPIRED");
    }
    if (status == 429) {
      return new PushSendResult(
          PushSendResult.Outcome.RATE_LIMITED, status, retryAfter, "PUSH_RATE_LIMITED");
    }
    if (status == 401 || status == 403) {
      return new PushSendResult(
          PushSendResult.Outcome.PERMANENT_FAILURE, status, null, "PUSH_VAPID_REJECTED");
    }
    if (status >= 500) {
      return new PushSendResult(
          PushSendResult.Outcome.TRANSIENT_FAILURE, status, null, "PUSH_SERVICE_UNAVAILABLE");
    }
    return new PushSendResult(
        PushSendResult.Outcome.PERMANENT_FAILURE, status, null, "PUSH_PROVIDER_REJECTED");
  }

  private void ensureProvider() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private Urgency parseUrgency(String value) {
    if (value == null) {
      return Urgency.NORMAL;
    }
    return switch (value.toLowerCase()) {
      case "very-low" -> Urgency.VERY_LOW;
      case "low" -> Urgency.LOW;
      case "high" -> Urgency.HIGH;
      default -> Urgency.NORMAL;
    };
  }

  private Duration parseRetryAfter(Header header) {
    if (header == null || header.getValue() == null) {
      return null;
    }
    String value = header.getValue().trim();
    try {
      long seconds = Long.parseLong(value);
      return Duration.ofSeconds(Math.max(1, seconds));
    } catch (NumberFormatException ignored) {
      try {
        Instant retryAt =
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        Duration delay = Duration.between(Instant.now(), retryAt);
        return delay.isNegative() || delay.isZero() ? Duration.ofSeconds(1) : delay;
      } catch (DateTimeParseException invalidDate) {
        return null;
      }
    }
  }
}
