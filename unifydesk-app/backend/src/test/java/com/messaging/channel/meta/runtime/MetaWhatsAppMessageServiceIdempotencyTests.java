package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.service.MediaService;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

class MetaWhatsAppMessageServiceIdempotencyTests {

  @Test
  void duplicateRequestReturnsExistingResultBeforeCurrentPolicyOrProviderChecks() {
    InboxConversationRepository conversations = mock(InboxConversationRepository.class);
    ChannelConnection connection = new ChannelConnection();
    connection.setId(20L);
    connection.setCompanyId(10L);
    connection.setChannelType(ChannelType.WHATSAPP);
    connection.setProviderType(ProviderType.META_WHATSAPP);
    connection.setStatus(ChannelConnectionStatus.DISCONNECTED);

    InboxConversation conversation = new InboxConversation();
    conversation.setId(40L);
    conversation.setCompanyId(10L);
    conversation.setConnection(connection);
    conversation.setProviderType(ProviderType.META_WHATSAPP);
    conversation.setExternalConversationId("wa:15550001111");
    when(conversations.findByIdAndCompanyId(40L, 10L)).thenReturn(Optional.of(conversation));

    MetaWhatsAppControlPlaneProjectionRepository controlPlane =
        mock(MetaWhatsAppControlPlaneProjectionRepository.class);
    MetaWhatsAppRuntimeGraphClient graphClient = mock(MetaWhatsAppRuntimeGraphClient.class);
    MetaWhatsAppTemplateService templates = mock(MetaWhatsAppTemplateService.class);
    MediaService mediaService = mock(MediaService.class);

    MetaWhatsAppMessageService service =
        new MetaWhatsAppMessageService(
            conversations,
            mock(InboxMessageRepository.class),
            controlPlane,
            mock(MetaWhatsAppCredentialResolver.class),
            graphClient,
            new MetaWhatsAppCustomerServicePolicy(new MetaWhatsAppRuntimeProperties()),
            templates,
            new MetaWhatsAppSendFailurePolicy(),
            mock(MetaWhatsAppOutboundReservationStore.class),
            mock(MetaWhatsAppRealtimePublisher.class),
            mediaService,
            mock(MessageStorageMeter.class),
            mock(StorageQuotaService.class),
            new ExistingResultJdbcTemplate(),
            mock(TransactionTemplate.class));

    var response =
        service.send(
            10L,
            40L,
            new MetaWhatsAppSendRequest(
                "same-request",
                "MEDIA",
                null,
                null,
                null,
                null,
                "deleted-media-id",
                "caption"));

    assertThat(response.messageId()).isEqualTo("50");
    assertThat(response.providerMessageId()).isEqualTo("wamid.original");
    assertThat(response.status()).isEqualTo("ACCEPTED");
    verifyNoInteractions(controlPlane, graphClient, templates, mediaService);
  }

  private static final class ExistingResultJdbcTemplate extends JdbcTemplate {
    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      assertThat(args).containsExactly(10L, 20L, 40L, "same-request");
      try {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("inbox_message_id")).thenReturn(50L);
        when(rs.getString("client_request_id")).thenReturn("same-request");
        when(rs.getString("provider_message_id")).thenReturn("wamid.original");
        when(rs.getString("detailed_status")).thenReturn("ACCEPTED");
        when(rs.getTimestamp("provider_status_at"))
            .thenReturn(Timestamp.from(Instant.parse("2026-09-29T00:00:00Z")));
        return List.of(rowMapper.mapRow(rs, 0));
      } catch (java.sql.SQLException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
