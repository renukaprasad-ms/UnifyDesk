package com.messaging.channel.meta.runtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.media.service.MediaService;
import java.time.Instant;
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
            mock(MetaWhatsAppTemplateService.class));
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
}
