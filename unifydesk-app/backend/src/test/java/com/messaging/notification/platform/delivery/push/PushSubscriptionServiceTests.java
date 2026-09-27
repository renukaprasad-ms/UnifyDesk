package com.messaging.notification.platform.delivery.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.PushKeys;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.RegisterPushSubscription;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PushSubscriptionServiceTests {

  private NotificationDeliveryRuntimeRepository repository;
  private CurrentUserService currentUserService;
  private CredentialCipher cipher;
  private BrowserPushReadiness readiness;
  private NotificationDeliveryProperties properties;
  private PushSubscriptionService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationDeliveryRuntimeRepository.class);
    currentUserService = mock(CurrentUserService.class);
    cipher = mock(CredentialCipher.class);
    readiness = mock(BrowserPushReadiness.class);
    properties = new NotificationDeliveryProperties();
    properties.getPush().setVapidPublicKey("public");
    properties.getPush().setVapidPrivateKey("private");
    when(readiness.ready()).thenReturn(true);
    service =
        new PushSubscriptionService(
            repository, currentUserService, cipher, properties, readiness);
  }

  @Test
  void sameUserEndpointRegistrationIsIdempotentAndRefreshesEncryptedSecrets() {
    when(currentUserService.currentUserId()).thenReturn(7L);
    PushSubscription existing = subscription(42L, 7L, "ACTIVE");
    when(repository.findSubscriptionByEndpointHash(anyString())).thenReturn(Optional.of(existing));
    when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    when(repository.refreshSubscription(
            anyLong(), anyLong(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(existing);

    var result = service.register(request());

    assertThat(result.created()).isFalse();
    assertThat(result.response().id()).isEqualTo("42");
    assertThat(result.response().toString()).doesNotContain("https://push.example");
    verify(repository).expireSubscriptionsForUser(7L, 20);
    verify(cipher).encrypt("https://push.example/subscription");
  }

  @Test
  void endpointCannotBeSilentlyReboundAcrossUsers() {
    when(currentUserService.currentUserId()).thenReturn(8L);
    when(repository.findSubscriptionByEndpointHash(anyString()))
        .thenReturn(Optional.of(subscription(42L, 7L, "ACTIVE")));

    assertThatThrownBy(() -> service.register(request())).isInstanceOf(ConflictException.class);
  }

  @Test
  void conflictSafeInsertMapsConcurrentDifferentOwnerToConflict() {
    when(currentUserService.currentUserId()).thenReturn(8L);
    PushSubscription winner = subscription(42L, 7L, "ACTIVE");
    when(repository.findSubscriptionByEndpointHash(anyString()))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(repository.activeSubscriptionCount(8L)).thenReturn(0);
    when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    when(repository.insertSubscriptionIfEndpointAvailable(
            anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.register(request()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Push subscription is already associated with another account");

    verify(repository).lockUserForPushRegistration(8L);
    verify(repository)
        .insertSubscriptionIfEndpointAvailable(
            anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void conflictSafeInsertPreservesSameUserIdempotency() {
    when(currentUserService.currentUserId()).thenReturn(7L);
    PushSubscription existing = subscription(42L, 7L, "ACTIVE");
    when(repository.findSubscriptionByEndpointHash(anyString()))
        .thenReturn(Optional.empty(), Optional.of(existing));
    when(repository.activeSubscriptionCount(7L)).thenReturn(0);
    when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    when(repository.insertSubscriptionIfEndpointAvailable(
            anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.empty());

    var result = service.register(request());

    assertThat(result.created()).isFalse();
    assertThat(result.response().id()).isEqualTo("42");
  }

  @Test
  void foreignSubscriptionDeleteFailsClosed() {
    when(currentUserService.currentUserId()).thenReturn(8L);
    when(repository.findSubscriptionForUser(42L, 8L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revoke(42L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void capabilityFailsClosedWhenCipherIsUnavailable() {
    when(readiness.ready()).thenReturn(false);

    var capability = service.capability();

    assertThat(capability.configured()).isFalse();
    assertThat(capability.vapidPublicKey()).isNull();
  }

  @Test
  void registrationFailsClosedWhenCipherIsUnavailable() {
    when(readiness.ready()).thenReturn(false);

    assertThatThrownBy(() -> service.register(request()))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("Browser push is not configured");
  }

  @Test
  void expiredExistingEndpointCannotReactivatePastValidActiveCap() {
    when(currentUserService.currentUserId()).thenReturn(7L);
    PushSubscription expired =
        new PushSubscription(
            42L,
            7L,
            "hash",
            "endpoint-ciphertext",
            "p256dh-ciphertext",
            "auth-ciphertext",
            Instant.parse("2026-09-26T00:00:00Z"),
            "Chrome",
            "ACTIVE",
            Instant.parse("2026-09-25T00:00:00Z"),
            null,
            null,
            null,
            Instant.parse("2026-09-25T00:00:00Z"));
    when(repository.findSubscriptionByEndpointHash(anyString())).thenReturn(Optional.of(expired));
    when(repository.activeSubscriptionCount(7L)).thenReturn(10);

    assertThatThrownBy(() -> service.register(request()))
        .isInstanceOf(com.messaging.common.exception.TooManyRequestsException.class);

    verify(repository).expireSubscriptionsForUser(7L, 20);
  }

  @Test
  void registrationLocksUserBeforeExpiryLookupCountAndInsert() {
    when(currentUserService.currentUserId()).thenReturn(7L);
    when(repository.findSubscriptionByEndpointHash(anyString())).thenReturn(Optional.empty());
    when(repository.activeSubscriptionCount(7L)).thenReturn(9);
    when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    when(repository.insertSubscriptionIfEndpointAvailable(
            anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.of(subscription(99L, 7L, "ACTIVE")));

    service.register(request());

    InOrder order = inOrder(repository);
    order.verify(repository).lockUserForPushRegistration(7L);
    order.verify(repository).expireSubscriptionsForUser(7L, 20);
    order.verify(repository).findSubscriptionByEndpointHash(anyString());
    order.verify(repository).activeSubscriptionCount(7L);
    order.verify(repository)
        .insertSubscriptionIfEndpointAvailable(
            anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void expiredActiveRowsAreExpiredBeforeCapIsEnforced() {
    when(currentUserService.currentUserId()).thenReturn(7L);
    when(repository.findSubscriptionByEndpointHash(anyString())).thenReturn(Optional.empty());
    when(repository.activeSubscriptionCount(7L)).thenReturn(9);
    when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    PushSubscription created = subscription(99L, 7L, "ACTIVE");
    when(repository.insertSubscriptionIfEndpointAvailable(
            anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Optional.of(created));

    var result = service.register(request());

    assertThat(result.created()).isTrue();
    assertThat(result.response().id()).isEqualTo("99");
    verify(repository).expireSubscriptionsForUser(7L, 20);
    verify(repository).activeSubscriptionCount(7L);
  }

  private RegisterPushSubscription request() {
    String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[65]);
    String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    return new RegisterPushSubscription(
        "https://push.example/subscription", null, new PushKeys(p256dh, auth), "Chrome");
  }

  private PushSubscription subscription(long id, long userId, String status) {
    Instant now = Instant.parse("2026-09-27T00:00:00Z");
    return new PushSubscription(
        id,
        userId,
        "hash",
        "endpoint-ciphertext",
        "p256dh-ciphertext",
        "auth-ciphertext",
        null,
        "Chrome",
        status,
        now,
        null,
        null,
        null,
        now);
  }
}
