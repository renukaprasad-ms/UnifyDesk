package com.messaging.broadcast.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.channel.meta.runtime.MetaWhatsAppControlPlaneProjectionRepository;
import com.messaging.channel.meta.runtime.MetaWhatsAppCredentialResolver;
import com.messaging.channel.meta.runtime.MetaWhatsAppOutboundRateLimiter;
import com.messaging.channel.meta.runtime.MetaWhatsAppProviderException;
import com.messaging.channel.meta.runtime.MetaWhatsAppRuntimeGraphClient;
import com.messaging.channel.meta.runtime.MetaWhatsAppSendFailurePolicy;
import com.messaging.common.exception.TooManyRequestsException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BroadcastWhatsAppDeliveryServiceTests {

  @Test
  void distributedRateLimitIsCheckedBeforeOpeningDeliveryAttempt() {
    Fixture fixture = new Fixture();
    doThrow(new TooManyRequestsException("WHATSAPP_RATE_LIMITED"))
        .when(fixture.rateLimiter)
        .requirePermit("phone-1");

    fixture.service.deliver(
        fixture.run(), fixture.connection(), fixture.sender(), fixture.recipient());

    verify(fixture.stateService)
        .releaseForRateLimit(eq(900L), any(java.time.Instant.class));
    verify(fixture.stateService, never()).begin(anyLong(), any());
    verifyNoInteractions(fixture.graphClient);
  }

  @Test
  void ambiguousProviderOutcomeMovesRecipientToUnknownWithoutBlindRetry() {
    Fixture fixture = new Fixture();
    BroadcastDeliveryStateService.AttemptContext attempt =
        new BroadcastDeliveryStateService.AttemptContext(900L, 700L, 901L, 1);
    when(fixture.stateService.begin(900L, BroadcastSenderProvider.META_WHATSAPP))
        .thenReturn(attempt);
    when(fixture.graphClient.sendTemplate(
            any(), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(
            new MetaWhatsAppProviderException(
                null,
                null,
                null,
                null,
                "NETWORK_AMBIGUOUS",
                false,
                true,
                null));

    fixture.service.deliver(
        fixture.run(), fixture.connection(), fixture.sender(), fixture.recipient());

    verify(fixture.stateService)
        .unknown(
            eq(attempt),
            eq("WHATSAPP_SEND_AMBIGUOUS"),
            contains("uncertain"));
    verify(fixture.stateService, never())
        .retry(eq(attempt), anyString(), anyString(), any(java.time.Instant.class));
  }

  private static final class Fixture {
    final MetaWhatsAppControlPlaneProjectionRepository projections =
        mock(MetaWhatsAppControlPlaneProjectionRepository.class);
    final MetaWhatsAppCredentialResolver credentials =
        mock(MetaWhatsAppCredentialResolver.class);
    final MetaWhatsAppRuntimeGraphClient graphClient =
        mock(MetaWhatsAppRuntimeGraphClient.class);
    final MetaWhatsAppOutboundRateLimiter rateLimiter =
        mock(MetaWhatsAppOutboundRateLimiter.class);
    final MetaWhatsAppSendFailurePolicy failurePolicy = new MetaWhatsAppSendFailurePolicy();
    final BroadcastDeliveryStateService stateService =
        mock(BroadcastDeliveryStateService.class);
    final BroadcastRetryPolicy retryPolicy = mock(BroadcastRetryPolicy.class);
    final BroadcastWhatsAppDeliveryService service =
        new BroadcastWhatsAppDeliveryService(
            projections,
            credentials,
            graphClient,
            rateLimiter,
            failurePolicy,
            stateService,
            retryPolicy,
            new ObjectMapper());

    Fixture() {
      when(projections.findByConnectionId(500L))
          .thenReturn(
              Optional.of(
                  new MetaWhatsAppControlPlaneProjectionRepository.Projection(
                      500L,
                      7L,
                      "waba",
                      "wa-account",
                      "msg-account",
                      "phone-1",
                      "ACTIVE",
                      "VALID",
                      "REGISTERED",
                      "SUBSCRIBED",
                      "NONE",
                      java.time.Instant.now())));
      when(credentials.require(500L))
          .thenReturn(
              new MetaWhatsAppCredentialResolver.StoredCredential(
                  "token", "waba", "phone-1", "v26.0"));
    }

    BroadcastRun run() {
      BroadcastRun run = new BroadcastRun();
      run.setId(700L);
      run.setCompanyId(7L);
      run.setProviderType(BroadcastSenderProvider.META_WHATSAPP);
      run.setWhatsAppTemplateNameSnapshot("promo");
      run.setWhatsAppTemplateLanguageSnapshot("en_US");
      run.setWhatsAppTemplateComponentsJsonSnapshot("[]");
      return run;
    }

    BroadcastSenderConnection connection() {
      BroadcastSenderConnection connection = new BroadcastSenderConnection();
      connection.setId(501L);
      connection.setCompanyId(7L);
      connection.setChannelConnectionId(500L);
      connection.setProvider(BroadcastSenderProvider.META_WHATSAPP);
      return connection;
    }

    BroadcastSender sender() {
      BroadcastSender sender = new BroadcastSender();
      sender.setId(55L);
      sender.setCompanyId(7L);
      sender.setProvider(BroadcastSenderProvider.META_WHATSAPP);
      sender.setVerifiedIdentity("phone-1");
      return sender;
    }

    BroadcastRecipient recipient() {
      BroadcastRecipient recipient = new BroadcastRecipient();
      recipient.setId(900L);
      recipient.setRunId(700L);
      recipient.setNormalizedDestination("+919876543210");
      return recipient;
    }
  }
}
