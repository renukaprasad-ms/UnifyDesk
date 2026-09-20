package com.messaging.channel.microsoft;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient.GraphResourceNotFoundException;
import com.messaging.channel.microsoft.MicrosoftApiClient.SubscriptionResponse;
import com.messaging.channel.microsoft.entity.MicrosoftSubscription;
import com.messaging.channel.microsoft.enums.MicrosoftSubscriptionStatus;
import com.messaging.channel.microsoft.repository.MicrosoftSubscriptionRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class MicrosoftSubscriptionService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final MicrosoftOAuthProperties properties;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftTokenService tokenService;
  private final MicrosoftSubscriptionRepository repository;
  private final ChannelConnectionRepository connectionRepository;
  private final MicrosoftSyncStateService syncStateService;

  public void ensure(Long connectionId) {
    if (!properties.webhookConfigured()) {
      return;
    }

    ChannelConnection connection =
        connectionRepository
            .findById(connectionId)
            .filter(
                item ->
                    item.getProviderType() == ProviderType.MICROSOFT
                        && item.getStatus() == ChannelConnectionStatus.ACTIVE)
            .orElseThrow(() -> new BadRequestException("Microsoft connection is not active"));

    MicrosoftSubscription existing = repository.findByConnectionId(connectionId).orElse(null);
    Instant renewThreshold = Instant.now().plus(properties.getSubscriptionRenewBefore());
    if (existing != null
        && existing.getStatus() == MicrosoftSubscriptionStatus.ACTIVE
        && existing.getExpirationAt().isAfter(renewThreshold)) {
      return;
    }

    String accessToken = tokenService.accessToken(connection);
    Instant requestedExpiration = Instant.now().plus(properties.getSubscriptionLifetime());

    if (existing != null && existing.getStatus() != MicrosoftSubscriptionStatus.DELETED) {
      try {
        SubscriptionResponse renewed =
            apiClient.renewSubscription(
                accessToken, existing.getExternalSubscriptionId(), requestedExpiration);
        existing.setExpirationAt(renewed.expirationInstant());
        existing.setStatus(MicrosoftSubscriptionStatus.ACTIVE);
        existing.setLastError(null);
        repository.save(existing);
        return;
      } catch (GraphResourceNotFoundException exception) {
        log.info(
            "Microsoft subscription no longer exists; recreating connectionId={}",
            connectionId);
      }
    }

    String clientState = clientState();
    SubscriptionResponse created =
        apiClient.createSubscription(
            accessToken, properties.getWebhookNotificationUrl(), clientState, requestedExpiration);

    MicrosoftSubscription subscription =
        existing == null ? new MicrosoftSubscription() : existing;
    subscription.setConnection(connection);
    subscription.setExternalSubscriptionId(created.id());
    subscription.setClientState(clientState);
    subscription.setResource(created.resource() == null ? "me/messages" : created.resource());
    subscription.setExpirationAt(created.expirationInstant());
    subscription.setStatus(MicrosoftSubscriptionStatus.ACTIVE);
    subscription.setLastError(null);
    repository.saveAndFlush(subscription);
  }

  public void processNotifications(List<ChangeNotification> notifications) {
    if (notifications == null || notifications.isEmpty()) return;

    for (ChangeNotification notification : notifications) {
      if (notification == null
          || notification.subscriptionId() == null
          || notification.clientState() == null) continue;

      MicrosoftSubscription subscription =
          repository
              .findByExternalSubscriptionId(notification.subscriptionId())
              .orElse(null);
      if (subscription == null
          || subscription.getStatus() != MicrosoftSubscriptionStatus.ACTIVE
          || !constantTimeEquals(subscription.getClientState(), notification.clientState())) {
        log.warn(
            "Ignoring invalid Microsoft notification subscriptionId={}",
            notification.subscriptionId());
        continue;
      }

      syncStateService.triggerConnection(subscription.getConnection().getId());
    }
  }

  @Transactional
  public void disconnect(ChannelConnection connection) {
    MicrosoftSubscription subscription =
        repository.findByConnectionId(connection.getId()).orElse(null);
    if (subscription == null) return;

    String accessToken = tokenService.storedAccessToken(connection);
    String externalSubscriptionId = subscription.getExternalSubscriptionId();

    subscription.setStatus(MicrosoftSubscriptionStatus.DELETED);
    repository.save(subscription);

    if (accessToken == null || accessToken.isBlank()) {
      return;
    }

    Runnable providerCleanup =
        () -> apiClient.deleteSubscriptionBestEffort(accessToken, externalSubscriptionId);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              providerCleanup.run();
            }
          });
    } else {
      providerCleanup.run();
    }
  }

  @Scheduled(fixedDelayString = "${app.channel.microsoft.subscription-renew-interval:12h}")
  public void renewExpiring() {
    if (!properties.webhookConfigured()) return;

    Instant cutoff = Instant.now().plus(properties.getSubscriptionRenewBefore());
    List<MicrosoftSubscription> expiring =
        repository.findByStatusAndExpirationAtBefore(MicrosoftSubscriptionStatus.ACTIVE, cutoff);
    for (MicrosoftSubscription subscription : expiring) {
      try {
        ensure(subscription.getConnection().getId());
      } catch (RuntimeException exception) {
        log.warn(
            "Microsoft subscription renewal scheduler failed connectionId={} reason={}",
            subscription.getConnection().getId(),
            exception.getClass().getSimpleName());
      }
    }
  }

  private String clientState() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private boolean constantTimeEquals(String left, String right) {
    if (left == null || right == null) return false;
    return java.security.MessageDigest.isEqual(
        left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public record ChangeNotification(
      String subscriptionId, String clientState, String changeType, String resource) {}
}
