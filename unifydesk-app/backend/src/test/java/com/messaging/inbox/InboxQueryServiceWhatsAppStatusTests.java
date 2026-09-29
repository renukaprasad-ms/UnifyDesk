package com.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.ConversationStatus;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.repository.InboxParticipantRepository;
import com.messaging.inbox.service.InboxQueryService;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class InboxQueryServiceWhatsAppStatusTests {

  @Test
  void detailReturnsDurableReadStatusWithoutChangingGenericSentStatus() {
    InboxConversationRepository conversations = mock(InboxConversationRepository.class);
    InboxParticipantRepository participants = mock(InboxParticipantRepository.class);
    InboxMessageRepository messages = mock(InboxMessageRepository.class);
    InboxMessageAttachmentRepository attachments = mock(InboxMessageAttachmentRepository.class);

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
    conversation.setStatus(ConversationStatus.OPEN);
    conversation.setLastMessageAt(Instant.parse("2026-09-29T00:00:00Z"));

    InboxMessage message = new InboxMessage();
    message.setId(50L);
    message.setConversation(conversation);
    message.setConnection(connection);
    message.setExternalMessageId("wamid.50");
    message.setProviderFolderKey("WHATSAPP");
    message.setDirection(InboxMessageDirection.OUTBOUND);
    message.setStatus(InboxMessageStatus.SENT);
    message.setSentAt(Instant.parse("2026-09-29T00:00:00Z"));
    message.setRead(true);

    when(conversations.findByIdAndCompanyId(40L, 10L)).thenReturn(Optional.of(conversation));
    when(messages.findByConversationIdAndStatusNotOrderBySentAtAscCreatedAtAsc(
            40L, InboxMessageStatus.DELETED))
        .thenReturn(List.of(message));
    when(participants.findByConversationIdInOrderByAddressAsc(List.of(40L)))
        .thenReturn(List.of());
    when(attachments.findByMessageIdInOrderByFilenameAsc(List.of(50L)))
        .thenReturn(List.of());

    InboxQueryService service =
        new InboxQueryService(
            conversations,
            participants,
            messages,
            attachments,
            mock(ChannelConnectionRepository.class),
            new ReadStatusJdbcTemplate());

    var detail = service.getConversationDetail(10L, 40L);

    assertThat(detail.messages()).hasSize(1);
    assertThat(detail.messages().getFirst().status()).isEqualTo(InboxMessageStatus.SENT);
    assertThat(detail.messages().getFirst().whatsappStatus()).isEqualTo("READ");
  }

  private static final class ReadStatusJdbcTemplate extends JdbcTemplate {
    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      assertThat(sql).contains("meta_whatsapp_message_runtime");
      assertThat(args).containsExactly(50L);
      try {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("inbox_message_id")).thenReturn(50L);
        when(resultSet.getString("detailed_status")).thenReturn("READ");
        return List.of(rowMapper.mapRow(resultSet, 0));
      } catch (java.sql.SQLException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
