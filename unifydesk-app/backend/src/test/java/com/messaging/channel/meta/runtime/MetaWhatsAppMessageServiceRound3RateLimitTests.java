package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class MetaWhatsAppMessageServiceRound3RateLimitTests {

  @Test
  void textAndTemplateKeepPermitImmediatelyBeforeSubmission() {
    Fixture text = fixture();
    when(text.graphClient.sendText(any(), any(), any(), any()))
        .thenReturn(new MetaWhatsAppRuntimeGraphClient.SendResult("wamid.text"));

    text.service.send(
        10L,
        40L,
        new MetaWhatsAppSendRequest(
            "request-text", "TEXT", "hello", null, null, null, null, null));

    InOrder textOrder = inOrder(text.limiter, text.graphClient);
    textOrder.verify(text.limiter).requirePermit("phone-1");
    textOrder.verify(text.graphClient).sendText(any(), any(), any(), any());

    Fixture template = fixture();
    when(template.templates.prepare(eq(10L), eq(20L), eq("70"), any()))
        .thenReturn(
            new MetaWhatsAppTemplateService.PreparedTemplate(
                70L, "order_update", "en_US", List.of()));
    when(template.graphClient.sendTemplate(any(), any(), any(), any(), any()))
        .thenReturn(new MetaWhatsAppRuntimeGraphClient.SendResult("wamid.template"));

    template.service.send(
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

    InOrder templateOrder = inOrder(template.limiter, template.graphClient);
    templateOrder.verify(template.limiter).requirePermit("phone-1");
    templateOrder.verify(template.graphClient).sendTemplate(any(), any(), any(), any(), any());
  }

  @Test
  void delayedMediaUploadAcquiresFreshPermitImmediatelyBeforeSend() throws Exception {
    Fixture fixture = fixture();
    Media media = media();
    AtomicBoolean uploadComplete = new AtomicBoolean();

    when(fixture.mediaService.requireActiveCompanyMedia(90L, 10L)).thenReturn(media);
    when(fixture.mediaService.readTrustedObject(90L, 10L)).thenReturn(new byte[] {1, 2, 3});
    when(fixture.graphClient.uploadMedia(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(Duration.ofMillis(20));
              uploadComplete.set(true);
              return "provider-media-1";
            });
    doAnswer(
            invocation -> {
              assertThat(uploadComplete).hasValue(true);
              return null;
            })
        .when(fixture.limiter)
        .requirePermit("phone-1");
    when(fixture.graphClient.sendMedia(any(), any(), any(), any(), any(), any()))
        .thenReturn(new MetaWhatsAppRuntimeGraphClient.SendResult("wamid.media"));

    fixture.service.send(
        10L,
        40L,
        new MetaWhatsAppSendRequest(
            "request-media-delay", "MEDIA", null, null, null, null, "90", "caption"));

    InOrder order = inOrder(fixture.graphClient, fixture.limiter);
    order.verify(fixture.graphClient).uploadMedia(any(), any(), any(), any());
    order.verify(fixture.limiter).requirePermit("phone-1");
    order.verify(fixture.graphClient).sendMedia(any(), any(), any(), any(), any(), any());
  }

  @Test
  void mediaRateRejectionAfterUploadPersistsFailedAndDuplicateDoesNotReplay() {
    Fixture fixture = fixture();
    prepareMedia(fixture);
    doThrow(new TooManyRequestsException("WHATSAPP_RATE_LIMITED"))
        .when(fixture.limiter)
        .requirePermit("phone-1");

    MetaWhatsAppSendRequest request =
        new MetaWhatsAppSendRequest(
            "request-media-rate", "MEDIA", null, null, null, null, "90", "caption");

    assertThatThrownBy(() -> fixture.service.send(10L, 40L, request))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");

    assertThat(fixture.jdbc.status).isEqualTo("FAILED");
    assertThat(fixture.savedMessage.get().getStatus()).isEqualTo(InboxMessageStatus.FAILED);
    verify(fixture.graphClient, times(1)).uploadMedia(any(), any(), any(), any());
    verify(fixture.graphClient, never()).sendMedia(any(), any(), any(), any(), any(), any());
    verify(fixture.realtimePublisher).statusChanged(10L, 20L, 40L, 50L);

    var duplicate = fixture.service.send(10L, 40L, request);

    assertThat(duplicate.status()).isEqualTo("FAILED");
    assertThat(duplicate.safeErrorCode()).isEqualTo("WHATSAPP_RATE_LIMITED");
    assertThat(duplicate.providerMessageId()).isNull();
    verify(fixture.graphClient, times(1)).uploadMedia(any(), any(), any(), any());
    verify(fixture.graphClient, never()).sendMedia(any(), any(), any(), any(), any(), any());
  }

  @Test
  void mediaRedisFailureAfterUploadPersistsFailedAndNeverSubmits() {
    Fixture fixture = fixture();
    prepareMedia(fixture);
    doThrow(new ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE"))
        .when(fixture.limiter)
        .requirePermit("phone-1");

    MetaWhatsAppSendRequest request =
        new MetaWhatsAppSendRequest(
            "request-media-redis", "MEDIA", null, null, null, null, "90", "caption");

    assertThatThrownBy(() -> fixture.service.send(10L, 40L, request))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("WHATSAPP_RATE_LIMIT_UNAVAILABLE");

    assertThat(fixture.jdbc.status).isEqualTo("FAILED");
    assertThat(fixture.savedMessage.get().getStatus()).isEqualTo(InboxMessageStatus.FAILED);
    verify(fixture.graphClient, never()).sendMedia(any(), any(), any(), any(), any(), any());

    var duplicate = fixture.service.send(10L, 40L, request);

    assertThat(duplicate.status()).isEqualTo("FAILED");
    assertThat(duplicate.safeErrorCode()).isEqualTo("WHATSAPP_RATE_LIMIT_UNAVAILABLE");
    verify(fixture.graphClient, times(1)).uploadMedia(any(), any(), any(), any());
    verify(fixture.graphClient, never()).sendMedia(any(), any(), any(), any(), any(), any());
  }

  @Test
  void provider429AfterMediaSubmissionStillImposesSharedCooldown() {
    Fixture fixture = fixture();
    prepareMedia(fixture);
    doNothing().when(fixture.limiter).requirePermit("phone-1");
    doThrow(
            new MetaWhatsAppProviderException(
                429,
                4,
                null,
                "trace",
                "WHATSAPP_PROVIDER_TEMPORARY",
                true,
                false,
                Duration.ofSeconds(12),
                null))
        .when(fixture.graphClient)
        .sendMedia(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(
            () ->
                fixture.service.send(
                    10L,
                    40L,
                    new MetaWhatsAppSendRequest(
                        "request-media-429",
                        "MEDIA",
                        null,
                        null,
                        null,
                        null,
                        "90",
                        "caption")))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("WHATSAPP_PROVIDER_TEMPORARY");

    InOrder order = inOrder(fixture.graphClient, fixture.limiter);
    order.verify(fixture.graphClient).uploadMedia(any(), any(), any(), any());
    order.verify(fixture.limiter).requirePermit("phone-1");
    order.verify(fixture.graphClient).sendMedia(any(), any(), any(), any(), any(), any());
    verify(fixture.limiter)
        .imposeProviderCooldown("phone-1", Duration.ofSeconds(12));
  }

  private void prepareMedia(Fixture fixture) {
    Media media = media();
    when(fixture.mediaService.requireActiveCompanyMedia(90L, 10L)).thenReturn(media);
    when(fixture.mediaService.readTrustedObject(90L, 10L)).thenReturn(new byte[] {1, 2, 3});
    when(fixture.graphClient.uploadMedia(any(), any(), any(), any()))
        .thenReturn("provider-media-1");
  }

  private Media media() {
    Media media = new Media();
    media.setId(90L);
    media.setCompanyId(10L);
    media.setOriginalFileName("photo.jpg");
    media.setContentType("image/jpeg");
    return media;
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
    MetaWhatsAppRealtimePublisher realtimePublisher = mock(MetaWhatsAppRealtimePublisher.class);
    MediaService mediaService = mock(MediaService.class);
    StatefulJdbcTemplate jdbc = new StatefulJdbcTemplate();
    AtomicReference<InboxMessage> savedMessage = new AtomicReference<>();

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
        .thenAnswer(
            invocation -> {
              jdbc.reserve(invocation.getArgument(2));
              return new MetaWhatsAppOutboundReservationStore.Reservation(60L, true);
            });
    when(messages.saveAndFlush(any(InboxMessage.class)))
        .thenAnswer(
            invocation -> {
              InboxMessage message = invocation.getArgument(0);
              message.setId(50L);
              savedMessage.set(message);
              return message;
            });
    when(messages.findById(50L))
        .thenAnswer(invocation -> Optional.ofNullable(savedMessage.get()));
    when(messages.save(any(InboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
            realtimePublisher,
            mediaService,
            mock(MessageStorageMeter.class),
            mock(StorageQuotaService.class),
            jdbc,
            new ImmediateTransactionTemplate());

    return new Fixture(
        service,
        graphClient,
        limiter,
        templates,
        realtimePublisher,
        mediaService,
        jdbc,
        savedMessage);
  }

  private record Fixture(
      MetaWhatsAppMessageService service,
      MetaWhatsAppRuntimeGraphClient graphClient,
      MetaWhatsAppOutboundRateLimiter limiter,
      MetaWhatsAppTemplateService templates,
      MetaWhatsAppRealtimePublisher realtimePublisher,
      MediaService mediaService,
      StatefulJdbcTemplate jdbc,
      AtomicReference<InboxMessage> savedMessage) {}

  private static final class StatefulJdbcTemplate extends JdbcTemplate {
    private String clientRequestId;
    private Long inboxMessageId;
    private String status;
    private String providerMessageId;
    private Integer errorCode;
    private Timestamp providerStatusAt;

    void reserve(String requestId) {
      clientRequestId = requestId;
      inboxMessageId = null;
      status = "PENDING";
      providerMessageId = null;
      errorCode = null;
      providerStatusAt = null;
    }

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
      if (!sql.contains("join inbox_messages") || status == null || inboxMessageId == null) {
        return List.of();
      }
      if (!clientRequestId.equals(args[3])) {
        return List.of();
      }
      try {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("inbox_message_id")).thenReturn(inboxMessageId);
        when(rs.getString("client_request_id")).thenReturn(clientRequestId);
        when(rs.getString("provider_message_id")).thenReturn(providerMessageId);
        when(rs.getString("detailed_status")).thenReturn(status);
        when(rs.getTimestamp("provider_status_at")).thenReturn(providerStatusAt);
        when(rs.getObject("last_provider_error_code", Integer.class)).thenReturn(errorCode);
        return List.of(rowMapper.mapRow(rs, 0));
      } catch (java.sql.SQLException exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public int update(String sql, Object... args) {
      if (sql.contains("set inbox_message_id=?")) {
        inboxMessageId = ((Number) args[0]).longValue();
        return 1;
      }
      if (sql.contains("detailed_status='FAILED'")) {
        status = "FAILED";
        errorCode = (Integer) args[0];
        providerMessageId = null;
        providerStatusAt = null;
        return 1;
      }
      if (sql.contains("set provider_message_id=?,detailed_status='ACCEPTED'")) {
        providerMessageId = (String) args[0];
        status = "ACCEPTED";
        providerStatusAt = (Timestamp) args[1];
        errorCode = null;
        return 1;
      }
      if (sql.contains("set detailed_status=?,status_rank=?")) {
        status = (String) args[0];
        errorCode = (Integer) args[3];
        providerStatusAt = (Timestamp) args[2];
        return 1;
      }
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
