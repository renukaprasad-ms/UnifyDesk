package com.messaging.channel.meta.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class MetaWhatsAppRound2RateLimitQaTests {

  @Test
  void textPermitIsImmediatelyBeforeMessageSubmission() {
    Fixture fixture = fixture();
    when(fixture.graphClient.sendText(any(), any(), any(), any()))
        .thenReturn(new MetaWhatsAppRuntimeGraphClient.SendResult("wamid.text"));

    fixture.service.send(
        10L,
        40L,
        new MetaWhatsAppSendRequest(
            "request-text", "TEXT", "hello", null, null, null, null, null));

    InOrder order = inOrder(fixture.limiter, fixture.graphClient);
    order.verify(fixture.limiter).requirePermit("phone-1");
    order.verify(fixture.graphClient).sendText(any(), any(), any(), any());
  }

  @Test
  void templatePermitIsImmediatelyBeforeMessageSubmission() {
    Fixture fixture = fixture();
    when(fixture.templates.prepare(eq(10L), eq(20L), eq("70"), any()))
        .thenReturn(
            new MetaWhatsAppTemplateService.PreparedTemplate(
                70L, "order_update", "en_US", List.of()));
    when(fixture.graphClient.sendTemplate(any(), any(), any(), any(), any()))
        .thenReturn(new MetaWhatsAppRuntimeGraphClient.SendResult("wamid.template"));

    fixture.service.send(
        10L,
        40L,
        new MetaWhatsAppSendRequest(
            "request-template",
            "TEMPLATE",
            null,
            null,
            "70",
            new MetaWhatsAppSendRequest.Parameters(List.of(), List.of(), List.of()),
            null,
            null));

    InOrder order = inOrder(fixture.limiter, fixture.graphClient);
    order.verify(fixture.limiter).requirePermit("phone-1");
    order.verify(fixture.graphClient).sendTemplate(any(), any(), any(), any(), any());
  }

  @Test
  void mediaPermitIsAcquiredAfterUploadImmediatelyBeforeMessageSubmission() {
    Fixture fixture = fixture();
    Media media = new Media();
    media.setId(90L);
    media.setCompanyId(10L);
    media.setOriginalFileName("photo.jpg");
    media.setContentType("image/jpeg");

    when(fixture.mediaService.requireActiveCompanyMedia(90L, 10L)).thenReturn(media);
    when(fixture.mediaService.readTrustedObject(90L, 10L)).thenReturn(new byte[] {1, 2, 3});
    when(fixture.graphClient.uploadMedia(any(), any(), any(), any())).thenReturn("provider-media-1");
    when(fixture.graphClient.sendMedia(any(), any(), any(), any(), any(), any()))
        .thenReturn(new MetaWhatsAppRuntimeGraphClient.SendResult("wamid.media"));

    fixture.service.send(
        10L,
        40L,
        new MetaWhatsAppSendRequest(
            "request-media", "MEDIA", null, null, null, null, "90", "caption"));

    InOrder order = inOrder(fixture.graphClient, fixture.limiter);
    order.verify(fixture.graphClient).uploadMedia(any(), any(), any(), any());
    order.verify(fixture.limiter).requirePermit("phone-1");
    order.verify(fixture.graphClient).sendMedia(any(), any(), any(), any(), any(), any());
  }

  private Fixture fixture() {
    InboxConversationRepository conversations = mock(InboxConversationRepository.class);
    InboxMessageRepository messages = mock(InboxMessageRepository.class);
    MetaWhatsAppControlPlaneProjectionRepository controlPlane =
        mock(MetaWhatsAppControlPlaneProjectionRepository.class);
    MetaWhatsAppCredentialResolver credentials = mock(MetaWhatsAppCredentialResolver.class);
    MetaWhatsAppRuntimeGraphClient graphClient = mock(MetaWhatsAppRuntimeGraphClient.class);
    MetaWhatsAppOutboundRateLimiter limiter = mock(MetaWhatsAppOutboundRateLimiter.class);
    MetaWhatsAppOutboundReservationStore reservations =
        mock(MetaWhatsAppOutboundReservationStore.class);
    MetaWhatsAppTemplateService templates = mock(MetaWhatsAppTemplateService.class);
    MediaService mediaService = mock(MediaService.class);

    ChannelConnection connection = new ChannelConnection();
    connection.setId(20L);
    connection.setCompanyId(10L);
    connection.setChannelType(ChannelType.WHATSAPP);
    connection.setProviderType(ProviderType.META_WHATSAPP);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);

    InboxConversation conversation = new InboxConversation();
    conversation.setId(40L);
    conversation.setCompanyId(10L);
    conversation.setConnection(connection);
    conversation.setProviderType(ProviderType.META_WHATSAPP);
    conversation.setExternalConversationId("wa:15550001111");
    when(conversations.findByIdAndCompanyId(40L, 10L)).thenReturn(Optional.of(conversation));

    when(controlPlane.findByConnectionId(20L))
        .thenReturn(
            Optional.of(
                new MetaWhatsAppControlPlaneProjectionRepository.Projection(
                    20L,
                    10L,
                    "waba-1",
                    "waba-1",
                    "waba-1",
                    "phone-1",
                    "ACTIVE",
                    "VALID",
                    "REGISTERED",
                    "SUBSCRIBED",
                    "DISABLED",
                    Instant.now())));
    when(credentials.require(20L))
        .thenReturn(
            new MetaWhatsAppCredentialResolver.StoredCredential(
                "token", "waba-1", "phone-1", "v26.0"));
    when(reservations.tryReserve(
            anyLong(), anyLong(), any(String.class), any(String.class), any(Instant.class)))
        .thenReturn(new MetaWhatsAppOutboundReservationStore.Reservation(60L, true));
    when(messages.saveAndFlush(any(InboxMessage.class)))
        .thenAnswer(
            invocation -> {
              InboxMessage message = invocation.getArgument(0);
              message.setId(50L);
              return message;
            });
    when(messages.findById(50L)).thenReturn(Optional.empty());

    MetaWhatsAppMessageService service =
        new MetaWhatsAppMessageService(
            conversations,
            messages,
            controlPlane,
            credentials,
            graphClient,
            new MetaWhatsAppCustomerServicePolicy(new MetaWhatsAppRuntimeProperties()),
            templates,
            new MetaWhatsAppSendFailurePolicy(),
            reservations,
            limiter,
            mock(MetaWhatsAppPendingOutcomeRecovery.class),
            mock(MetaWhatsAppRealtimePublisher.class),
            mediaService,
            mock(MessageStorageMeter.class),
            mock(StorageQuotaService.class),
            new QaJdbcTemplate(),
            new ImmediateTransactionTemplate());

    return new Fixture(service, graphClient, limiter, templates, mediaService);
  }

  private record Fixture(
      MetaWhatsAppMessageService service,
      MetaWhatsAppRuntimeGraphClient graphClient,
      MetaWhatsAppOutboundRateLimiter limiter,
      MetaWhatsAppTemplateService templates,
      MediaService mediaService) {}

  private static final class QaJdbcTemplate extends JdbcTemplate {
    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
      return (T) Integer.valueOf(1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      if (sql.contains("last_customer_message_at")) {
        return (List<T>) List.of(Instant.now());
      }
      return List.of();
    }

    @Override
    public int update(String sql, Object... args) {
      return 1;
    }
  }

  private static final class ImmediateTransactionTemplate extends TransactionTemplate {
    @Override
    public <T> T execute(TransactionCallback<T> action) {
      return action.doInTransaction(new SimpleTransactionStatus());
    }
  }
}
