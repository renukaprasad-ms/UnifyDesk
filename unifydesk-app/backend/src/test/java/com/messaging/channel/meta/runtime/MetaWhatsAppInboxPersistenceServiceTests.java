package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.service.InboxMessagePostProcessor;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetaWhatsAppInboxPersistenceServiceTests {

  @Test
  void duplicateInboundTextPersistsAndNotifiesExactlyOnce() {
    MetaWhatsAppTenantResolver tenantResolver = mock(MetaWhatsAppTenantResolver.class);
    var tenant =
        new MetaWhatsAppTenantResolver.ResolvedTenant(
            10L,
            20L,
            30L,
            ChannelConnectionStatus.ACTIVE,
            SenderIdentityStatus.ACTIVE,
            "phone-1");
    when(tenantResolver.resolveTrustedPhone("phone-1")).thenReturn(tenant);

    ChannelConnection connection = new ChannelConnection();
    connection.setId(20L);
    connection.setCompanyId(10L);
    connection.setCreatedByUserId(7L);
    connection.setProviderType(ProviderType.META_WHATSAPP);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    ChannelConnectionRepository connectionRepository = mock(ChannelConnectionRepository.class);
    when(connectionRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(connection));

    InboxConversation conversation = new InboxConversation();
    conversation.setId(40L);
    conversation.setCompanyId(10L);
    conversation.setConnection(connection);
    conversation.setProviderType(ProviderType.META_WHATSAPP);
    InboxConversationRepository conversationRepository =
        mock(InboxConversationRepository.class);
    when(conversationRepository.findByConnectionIdAndExternalConversationId(20L, "wa:15550001111"))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.save(any(InboxConversation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AtomicReference<InboxMessage> saved = new AtomicReference<>();
    InboxMessageRepository messageRepository = mock(InboxMessageRepository.class);
    when(messageRepository.findByConnectionIdAndExternalMessageId(20L, "wamid.1"))
        .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
    when(messageRepository.saveAndFlush(any(InboxMessage.class)))
        .thenAnswer(
            invocation -> {
              InboxMessage message = invocation.getArgument(0);
              message.setId(50L);
              saved.set(message);
              return message;
            });
    when(messageRepository.countByConversationIdAndDirectionAndReadFalse(anyLong(), any()))
        .thenReturn(1L);

    MessageStorageMeter meter = mock(MessageStorageMeter.class);
    when(meter.measure(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(64L);
    StorageQuotaService quota = mock(StorageQuotaService.class);
    InboxMessagePostProcessor postProcessor = mock(InboxMessagePostProcessor.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    MetaWhatsAppInboxPersistenceService service =
        new MetaWhatsAppInboxPersistenceService(
            new ObjectMapper(),
            new MetaWhatsAppInboundMessageMapper(),
            tenantResolver,
            connectionRepository,
            conversationRepository,
            messageRepository,
            mock(InboxMessageAttachmentRepository.class),
            meter,
            quota,
            List.of(postProcessor),
            mock(MetaWhatsAppRealtimePublisher.class),
            jdbcTemplate);

    var event =
        new MetaWhatsAppWebhookEventStore.ClaimedEvent(
            1L,
            "MESSAGE",
            "phone-1",
            "wamid.1",
            Instant.parse("2026-09-29T00:00:00Z"),
            """
            {
              "phoneNumberId":"phone-1",
              "contacts":[{"profile":{"name":"Customer"}}],
              "message":{
                "id":"wamid.1",
                "from":"15550001111",
                "type":"text",
                "text":{"body":"hello"}
              }
            }
            """,
            1,
            "claim-1");

    var first = service.persistInbound(event);
    var duplicate = service.persistInbound(event);

    assertThat(first.created()).isTrue();
    assertThat(duplicate.created()).isFalse();
    assertThat(saved.get().getBodyText()).isEqualTo("hello");
    assertThat(saved.get().isRead()).isFalse();
    verify(postProcessor, times(1)).afterPersist(any(InboxMessage.class));
    verify(quota, times(1)).consume(anyLong(), any(), anyString(), anyString(), anyLong());
  }
}
