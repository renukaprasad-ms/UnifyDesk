package com.messaging.channel.meta.runtime;

import com.messaging.common.exception.NotFoundException;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppWebhookWorker {
  private final MetaWhatsAppWebhookEventStore eventStore;
  private final MetaWhatsAppInboxPersistenceService inboxPersistence;
  private final MetaWhatsAppStatusService statusService;
  private final MetaWhatsAppCredentialResolver credentialResolver;
  private final MetaWhatsAppRuntimeGraphClient graphClient;
  private final MetaWhatsAppRuntimeProperties properties;
  private final MediaService mediaService;
  private final MetaWhatsAppTemplateService templateService;

  @Scheduled(fixedDelayString = "${app.channel.meta-whatsapp.runtime.worker-delay-ms:1000}")
  public void processDue() {
    for (var event : eventStore.claimDue()) {
      processOne(event);
    }
  }

  @Scheduled(cron = "${app.channel.meta-whatsapp.runtime.cleanup-cron:0 20 3 * * *}")
  public void cleanup() {
    eventStore.purge();
  }

  void processOne(MetaWhatsAppWebhookEventStore.ClaimedEvent event) {
    try {
      switch (event.eventKind()) {
        case "MESSAGE" -> processMessage(event);
        case "STATUS" -> {
          if (statusService.apply(event)) eventStore.markProcessed(event);
          else eventStore.markIgnored(event, "WHATSAPP_STATUS_UNKNOWN_MESSAGE");
        }
        case "TEMPLATE_STATUS" -> {
          if (templateService.applyWebhookStatus(event.payloadJson())) {
            eventStore.markProcessed(event);
          } else {
            eventStore.markIgnored(event, "WHATSAPP_TEMPLATE_STATUS_UNKNOWN");
          }
        }
        case "ACCOUNT_UPDATE" ->
            eventStore.markIgnored(event, "WHATSAPP_ACCOUNT_UPDATE_RECORDED");
        default -> eventStore.markIgnored(event, "WHATSAPP_UNKNOWN_EVENT");
      }
    } catch (NotFoundException exception) {
      eventStore.markIgnored(event, "WHATSAPP_UNKNOWN_PHONE");
    } catch (MetaWhatsAppProviderException exception) {
      eventStore.fail(event, exception.safeCategory(), exception.retryable());
    } catch (MetaWhatsAppRuntimeException exception) {
      eventStore.fail(event, exception.safeCode(), exception.retryable());
    } catch (TransientDataAccessException exception) {
      eventStore.fail(event, "WHATSAPP_DB_TEMPORARY", true);
    } catch (RuntimeException exception) {
      // Unknown application failures are retried with the bounded event-attempt policy. This is
      // important for transactional post-processors: a rollback must not permanently drop the
      // inbound message or its notification side effect.
      eventStore.fail(event, "WHATSAPP_PROCESSING_FAILED", true);
    }
  }

  private void processMessage(MetaWhatsAppWebhookEventStore.ClaimedEvent event) {
    var inbound = inboxPersistence.persistInbound(event);
    if (StringUtils.hasText(inbound.providerMediaId())
        && !inboxPersistence.hasMediaLink(
            inbound.inboxMessageId(), inbound.providerMediaId())) {
      var credential = credentialResolver.require(inbound.connectionId());
      var metadata = graphClient.mediaMetadata(credential, inbound.providerMediaId());
      if (!StringUtils.hasText(metadata.url())) {
        throw new MetaWhatsAppRuntimeException(
            "WHATSAPP_MEDIA_INVALID", false, "Provider returned no media URL");
      }
      if (metadata.fileSize() != null
          && metadata.fileSize() > properties.getInboundMediaHardLimit().toBytes()) {
        throw new MetaWhatsAppRuntimeException(
            "WHATSAPP_MEDIA_INVALID", false, "Provider media exceeds hard limit");
      }
      String mime =
          StringUtils.hasText(metadata.mimeType()) ? metadata.mimeType() : inbound.mimeType();
      String fileName =
          StringUtils.hasText(inbound.fileName())
              ? inbound.fileName()
              : inbound.providerMediaId() + extension(mime);
      byte[] content =
          graphClient.downloadAuthenticated(
              metadata.url(),
              credential.accessToken(),
              properties.getInboundMediaHardLimit().toBytes());
      Media media =
          mediaService.ingestTrustedCompanyMedia(
              inbound.companyId(), inbound.ownerUserId(), fileName, mime, content);
      inboxPersistence.linkMedia(
          inbound.inboxMessageId(), inbound.providerMediaId(), fileName, mime, media);
    }
    eventStore.markProcessed(event);
  }

  private String extension(String mime) {
    if (mime == null) return ".bin";
    return switch (mime.toLowerCase(Locale.ROOT)) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "application/pdf" -> ".pdf";
      default -> ".bin";
    };
  }
}
