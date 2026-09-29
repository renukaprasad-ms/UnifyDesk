package com.messaging.channel.meta.runtime;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.service.MediaService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetaWhatsAppWebhookWorkerTests {

  @Test
  void transactionalInboundFailureIsRetriedInsteadOfDropped() {
    MetaWhatsAppWebhookEventStore eventStore = mock(MetaWhatsAppWebhookEventStore.class);
    MetaWhatsAppInboxPersistenceService inbox =
        mock(MetaWhatsAppInboxPersistenceService.class);
    MetaWhatsAppWebhookWorker worker =
        new MetaWhatsAppWebhookWorker(
            eventStore,
            inbox,
            mock(MetaWhatsAppStatusService.class),
            mock(MetaWhatsAppCredentialResolver.class),
            mock(MetaWhatsAppRuntimeGraphClient.class),
            new MetaWhatsAppRuntimeProperties(),
            mock(MediaService.class),
            mock(MetaWhatsAppTemplateService.class),
            mock(MetaWhatsAppProviderMediaReservationStore.class));
    var event =
        new MetaWhatsAppWebhookEventStore.ClaimedEvent(
            1L,
            "MESSAGE",
            "phone-1",
            "wamid.1",
            Instant.parse("2026-09-29T00:00:00Z"),
            "{}",
            1,
            "claim-1");
    when(inbox.persistInbound(event)).thenThrow(new RuntimeException("temporary hook failure"));

    worker.processOne(event);

    verify(eventStore).fail(event, "WHATSAPP_PROCESSING_FAILED", true);
  }

  @Test
  void retryAfterCrashBetweenMediaIngestAndLinkReusesReservedMedia() {
    MetaWhatsAppWebhookEventStore eventStore = mock(MetaWhatsAppWebhookEventStore.class);
    MetaWhatsAppInboxPersistenceService inbox = mock(MetaWhatsAppInboxPersistenceService.class);
    MetaWhatsAppCredentialResolver credentials = mock(MetaWhatsAppCredentialResolver.class);
    MetaWhatsAppRuntimeGraphClient graphClient = mock(MetaWhatsAppRuntimeGraphClient.class);
    MediaService mediaService = mock(MediaService.class);
    MetaWhatsAppProviderMediaReservationStore reservations =
        mock(MetaWhatsAppProviderMediaReservationStore.class);

    MetaWhatsAppWebhookWorker worker =
        new MetaWhatsAppWebhookWorker(
            eventStore,
            inbox,
            mock(MetaWhatsAppStatusService.class),
            credentials,
            graphClient,
            new MetaWhatsAppRuntimeProperties(),
            mediaService,
            mock(MetaWhatsAppTemplateService.class),
            reservations);

    var event =
        new MetaWhatsAppWebhookEventStore.ClaimedEvent(
            2L,
            "MESSAGE",
            "phone-1",
            "wamid.media",
            Instant.parse("2026-09-29T00:00:00Z"),
            "{}",
            1,
            "claim-media");
    var inbound =
        new MetaWhatsAppInboxPersistenceService.InboundResult(
            10L, 20L, 7L, 30L, "media-1", "image/jpeg", "photo.jpg", true);
    var reservation =
        new MetaWhatsAppProviderMediaReservationStore.Reservation(
            40L, 20L, 30L, "media-1", 99L, "RESERVED");
    Media media = new Media();
    media.setId(99L);
    media.setOwnerUserId(7L);
    media.setCompanyId(10L);
    media.setOriginalFileName("photo.jpg");
    media.setContentType("image/jpeg");
    media.setSizeBytes(3L);
    media.setStatus(MediaStatus.ACTIVE);

    when(inbox.persistInbound(event)).thenReturn(inbound);
    when(reservations.reserveOrGet(20L, 30L, "media-1")).thenReturn(reservation);
    when(inbox.hasMediaLink(30L, "media-1")).thenReturn(false);
    when(mediaService.findActiveTrustedCompanyMedia(99L, 10L))
        .thenReturn(Optional.empty(), Optional.of(media));
    when(credentials.require(20L))
        .thenReturn(
            new MetaWhatsAppCredentialResolver.StoredCredential(
                "token", "waba-1", "phone-1", "v24.0"));
    when(graphClient.mediaMetadata(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("media-1")))
        .thenReturn(
            new MetaWhatsAppRuntimeGraphClient.MediaMetadata(
                "https://lookaside.fbsbx.com/media", "image/jpeg", 3L, null));
    when(graphClient.downloadAuthenticated(
            "https://lookaside.fbsbx.com/media", "token", 20L * 1024L * 1024L))
        .thenReturn(new byte[] {1, 2, 3});
    when(mediaService.ingestTrustedCompanyMediaWithId(
            99L,
            10L,
            7L,
            "photo.jpg",
            "image/jpeg",
            new byte[] {1, 2, 3}))
        .thenReturn(media);
    doThrow(new RuntimeException("crash after ingest"))
        .doNothing()
        .when(inbox)
        .linkMedia(30L, "media-1", "photo.jpg", "image/jpeg", media);

    worker.processOne(event);
    worker.processOne(event);

    verify(mediaService, times(1))
        .ingestTrustedCompanyMediaWithId(
            99L,
            10L,
            7L,
            "photo.jpg",
            "image/jpeg",
            new byte[] {1, 2, 3});
    verify(reservations).markFailed(40L);
    verify(reservations).markLinked(40L);
    verify(eventStore).fail(event, "WHATSAPP_PROCESSING_FAILED", true);
    verify(eventStore).markProcessed(event);
  }
}
