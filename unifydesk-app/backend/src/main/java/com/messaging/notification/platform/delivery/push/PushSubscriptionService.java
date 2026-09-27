package com.messaging.notification.platform.delivery.push;

import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

  private static final Pattern BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");

  private final NotificationDeliveryRuntimeRepository repository;
  private final CurrentUserService currentUserService;
  private final CredentialCipher credentialCipher;
  private final NotificationDeliveryProperties properties;
  private final BrowserPushReadiness pushReadiness;

  public Capability capability() {
    boolean configured = pushReadiness.ready();
    return new Capability(
        configured,
        configured ? properties.getPush().getVapidPublicKey() : null,
        properties.getPush().getMaxPayloadBytes());
  }

  @Transactional
  public RegistrationResult register(RegisterPushSubscription request) {
    if (!pushReadiness.ready()) {
      throw new ServiceUnavailableException("Browser push is not configured");
    }
    validate(request);
    long userId = currentUserService.currentUserId();
    int cap = Math.max(1, properties.getPush().getMaxActiveSubscriptions());
    repository.lockUserForPushRegistration(userId);
    repository.expireSubscriptionsForUser(userId, Math.max(cap * 2, 20));
    String hash = endpointHash(request.endpoint());
    PushSubscription existing = repository.findSubscriptionByEndpointHash(hash).orElse(null);
    if (existing != null) {
      if (existing.userId() != userId) {
        throw new ConflictException(
            "Push subscription is already associated with another account");
      }
      if (!isCurrentlyActive(existing)
          && repository.activeSubscriptionCount(userId) >= cap) {
        throw new TooManyRequestsException(
            "Maximum active browser notification subscriptions reached");
      }
      PushSubscription refreshed =
          repository.refreshSubscription(
              existing.id(),
              userId,
              encryptSecret(request.endpoint()),
              encryptSecret(request.keys().p256dh()),
              encryptSecret(request.keys().auth()),
              expiration(request.expirationTime()),
              label(request.deviceLabel()));
      return new RegistrationResult(toResponse(refreshed), false);
    }

    if (repository.activeSubscriptionCount(userId) >= cap) {
      throw new TooManyRequestsException(
          "Maximum active browser notification subscriptions reached");
    }
    Optional<PushSubscription> inserted =
        repository.insertSubscriptionIfEndpointAvailable(
            userId,
            hash,
            encryptSecret(request.endpoint()),
            encryptSecret(request.keys().p256dh()),
            encryptSecret(request.keys().auth()),
            expiration(request.expirationTime()),
            label(request.deviceLabel()));
    if (inserted.isPresent()) {
      return new RegistrationResult(toResponse(inserted.get()), true);
    }

    PushSubscription concurrentOwner =
        repository
            .findSubscriptionByEndpointHash(hash)
            .orElseThrow(
                () ->
                    new ConflictException(
                        "Push subscription is already associated with another account"));
    if (concurrentOwner.userId() != userId) {
      throw new ConflictException(
          "Push subscription is already associated with another account");
    }
    return new RegistrationResult(toResponse(concurrentOwner), false);
  }

  public List<SubscriptionResponse> list() {
    long userId = currentUserService.currentUserId();
    return repository.subscriptionsForUser(userId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public RevokeResponse revoke(long subscriptionId) {
    long userId = currentUserService.currentUserId();
    repository
        .findSubscriptionForUser(subscriptionId, userId)
        .orElseThrow(() -> new NotFoundException("Push subscription not found"));
    repository.revokeSubscription(subscriptionId, userId);
    PushSubscription updated =
        repository
            .findSubscriptionForUser(subscriptionId, userId)
            .orElseThrow(() -> new NotFoundException("Push subscription not found"));
    return new RevokeResponse(Long.toString(updated.id()), updated.status());
  }

  public DecryptedSubscription decrypt(PushSubscription subscription) {
    return new DecryptedSubscription(
        credentialCipher.decrypt(subscription.endpointCiphertext()),
        credentialCipher.decrypt(subscription.p256dhCiphertext()),
        credentialCipher.decrypt(subscription.authCiphertext()));
  }

  private boolean isCurrentlyActive(PushSubscription subscription) {
    if (!"ACTIVE".equals(subscription.status())) {
      return false;
    }
    return subscription.expirationTime() == null
        || subscription.expirationTime().isAfter(Instant.now());
  }

  private String encryptSecret(String value) {
    try {
      return credentialCipher.encrypt(value);
    } catch (IllegalStateException exception) {
      throw new ServiceUnavailableException(
          "Browser push encryption is not configured", exception);
    }
  }

  private void validate(RegisterPushSubscription request) {
    if (request == null || request.keys() == null) {
      throw new BadRequestException("Invalid push subscription");
    }
    String endpoint = request.endpoint();
    if (endpoint == null || endpoint.isBlank() || endpoint.length() > 2048) {
      throw new BadRequestException("Invalid push endpoint");
    }
    try {
      URI uri = URI.create(endpoint);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new BadRequestException("Push endpoint must use HTTPS");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid push endpoint");
    }
    validateKey(request.keys().p256dh(), 32, 256, "p256dh");
    validateKey(request.keys().auth(), 8, 128, "auth");
  }

  private void validateKey(String value, int minBytes, int maxBytes, String name) {
    if (value == null
        || value.length() > 512
        || !BASE64URL.matcher(value).matches()) {
      throw new BadRequestException("Invalid push " + name + " key");
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(value);
      if (decoded.length < minBytes || decoded.length > maxBytes) {
        throw new BadRequestException("Invalid push " + name + " key");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid push " + name + " key");
    }
  }

  private Instant expiration(Long millis) {
    return millis == null ? null : Instant.ofEpochMilli(millis);
  }

  private String label(String value) {
    if (value == null) {
      return null;
    }
    String clean = value.replaceAll("[\\p{Cntrl}]", " ").trim();
    return clean.length() > 120 ? clean.substring(0, 120) : clean;
  }

  private String endpointHash(String endpoint) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private SubscriptionResponse toResponse(PushSubscription subscription) {
    return new SubscriptionResponse(
        Long.toString(subscription.id()),
        subscription.status(),
        subscription.deviceLabel(),
        subscription.createdAt(),
        subscription.lastSuccessAt());
  }

  public record Capability(boolean configured, String vapidPublicKey, int maxPayloadBytes) {}

  public record PushKeys(String p256dh, String auth) {}

  public record RegisterPushSubscription(
      String endpoint, Long expirationTime, PushKeys keys, String deviceLabel) {}

  public record SubscriptionResponse(
      String id, String status, String deviceLabel, Instant createdAt, Instant lastSuccessAt) {}

  public record RegistrationResult(SubscriptionResponse response, boolean created) {}

  public record RevokeResponse(String id, String status) {}

  public record DecryptedSubscription(String endpoint, String p256dh, String auth) {}
}
