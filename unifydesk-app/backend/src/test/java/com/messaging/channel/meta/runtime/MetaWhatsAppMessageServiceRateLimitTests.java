package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.service.MediaService;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class MetaWhatsAppMessageServiceRateLimitTests {

  @Test
  void limiterRejectionDoesNotSubmitToMeta() {
    Fixture fixture = fixture();
    doThrow(new TooManyRequestsException("WHATSAPP_RATE_LIMITED"))
        .when(fixture.limiter)
        .requirePermit("phone-1");

    assertThatThrownBy(
            () ->
                fixture.service.send(
                    10L,
                    40L,
                    new MetaWhatsAppSendRequest(
                        "request-rate",
                        "TEXT",
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessage("WHATSAPP_RATE_LIMITED");

    verify(fixture.limiter).requirePermit("phone-1");
    verifyNoInteractions(fixture.graphClient);
  }

  @Test
  void provider429ImposesSharedRetryAfterCooldownWithoutReplay() {
    Fixture fixture = fixture();
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
        .sendText(any(), any(), any(), any());

    assertThatThrownBy(
            () ->
                fixture.service.send(
                    10L,
                    40L,
                    new MetaWhatsAppSendRequest(
                        "request-429",
                        "TEXT",
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("WHATSAPP_PROVIDER_TEMPORARY");

    verify(fixture.limiter)
        .imposeProviderCooldown("phone-1", Duration.ofSeconds(12));
    verify(fixture.graphClient, times(1)).sendText(any(), any(), any(), any());
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
                "token", "waba-1", "phone-1", "v24.0"));
    when(reservations.tryReserve(
            any(Long.class),
            any(Long.class),
            any(String.class),
            any(String.class),
            any(Instant.class)))
        .thenReturn(new MetaWhatsAppOutboundReservationStore.Reservation(60L, true));
    when(messages.saveAndFlush(any(InboxMessage.class)))
        .thenAnswer(
            invocation -> {
              InboxMessage message = invocation.getArgument(0);
              message.setId(50L);
              return message;
            });

    MetaWhatsAppMessageService service =
        new MetaWhatsAppMessageService(
            conversations,
            messages,
            controlPlane,
            credentials,
            graphClient,
            new MetaWhatsAppCustomerServicePolicy(new MetaWhatsAppRuntimeProperties()),
            mock(MetaWhatsAppTemplateService.class),
            new MetaWhatsAppSendFailurePolicy(),
            reservations,
            limiter,
            mock(MetaWhatsAppPendingOutcomeRecovery.class),
            mock(MetaWhatsAppRealtimePublisher.class),
            mock(MediaService.class),
            mock(MessageStorageMeter.class),
            mock(StorageQuotaService.class),
            new SendJdbcTemplate(),
            new ImmediateTransactionTemplate());

    return new Fixture(service, graphClient, limiter);
  }

  private record Fixture(
      MetaWhatsAppMessageService service,
      MetaWhatsAppRuntimeGraphClient graphClient,
      MetaWhatsAppOutboundRateLimiter limiter) {}

  private static final class SendJdbcTemplate extends JdbcTemplate {
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

    @Override
    public void executeWithoutResult(Consumer<TransactionStatus> action) {
      action.accept(new SimpleTransactionStatus());
    }
  }
}
