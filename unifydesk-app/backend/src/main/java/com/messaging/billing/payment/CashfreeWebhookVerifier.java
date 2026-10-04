package com.messaging.billing.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CashfreeWebhookVerifier {

  private static final long MAX_CONFIGURED_PAST_AGE_SECONDS = 3600L;
  private static final long MAX_CONFIGURED_FUTURE_SKEW_SECONDS = 600L;

  private final CashfreeProperties properties;
  private final Clock clock;

  @Autowired
  public CashfreeWebhookVerifier(CashfreeProperties properties) {
    this(properties, Clock.systemUTC());
  }

  CashfreeWebhookVerifier(CashfreeProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public boolean verify(byte[] rawBody, String timestamp, String signature) {
    if (rawBody == null
        || timestamp == null
        || timestamp.isBlank()
        || signature == null
        || signature.isBlank()
        || properties.getClientSecret() == null
        || properties.getClientSecret().isBlank()
        || !isFresh(timestamp)) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
      byte[] expected = mac.doFinal(rawBody);
      byte[] supplied = Base64.getDecoder().decode(signature);
      return MessageDigest.isEqual(expected, supplied);
    } catch (Exception exception) {
      return false;
    }
  }

  private boolean isFresh(String timestamp) {
    long maxAgeSeconds = properties.getWebhookMaxAgeSeconds();
    long futureSkewSeconds = properties.getWebhookFutureSkewSeconds();
    if (maxAgeSeconds <= 0
        || maxAgeSeconds > MAX_CONFIGURED_PAST_AGE_SECONDS
        || futureSkewSeconds <= 0
        || futureSkewSeconds > MAX_CONFIGURED_FUTURE_SKEW_SECONDS) {
      return false;
    }

    if (!timestamp.chars().allMatch(Character::isDigit)) {
      return false;
    }

    try {
      long numeric = Long.parseLong(timestamp);
      Instant now = clock.instant();
      Instant oldest = now.minusSeconds(maxAgeSeconds);
      Instant newest = now.plusSeconds(futureSkewSeconds);

      boolean secondsFresh = isWithin(Instant.ofEpochSecond(numeric), oldest, newest);
      boolean millisFresh = isWithin(Instant.ofEpochMilli(numeric), oldest, newest);

      // The pinned Cashfree material used by Billing R1 specifies the signed timestamp header
      // but does not pin its numeric unit. Accept only a representation that independently lands
      // inside the configured freshness window. Exactly one representation must match.
      return secondsFresh ^ millisFresh;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean isWithin(Instant value, Instant oldest, Instant newest) {
    return !value.isBefore(oldest) && !value.isAfter(newest);
  }
}
