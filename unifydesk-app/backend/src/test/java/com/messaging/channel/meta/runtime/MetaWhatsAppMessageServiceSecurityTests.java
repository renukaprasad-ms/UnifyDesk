package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.exception.MediaException;
import com.messaging.media.service.MediaService;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

class MetaWhatsAppMessageServiceSecurityTests {

  @Test
  void foreignConversationIsRejectedBeforeProviderLookup() {
    Fixture fixture = fixture(false);

    assertThatThrownBy(
            () ->
                fixture.service.send(
                    10L,
                    999L,
                    new MetaWhatsAppSendRequest(
                        "request-1",
                        "TEXT",
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void foreignTemplateIsRejectedBeforeProviderSubmission() {
    Fixture fixture = fixture(true);
    when(fixture.templateService.prepare(eq(10L), eq(20L), eq("999"), any()))
        .thenThrow(new NotFoundException("WhatsApp template not found"));

    assertThatThrownBy(
            () ->
                fixture.service.send(
                    10L,
                    40L,
                    new MetaWhatsAppSendRequest(
                        "request-2",
                        "TEMPLATE",
                        null,
                        null,
                        "999",
                        new MetaWhatsAppSendRequest.Parameters(
                            List.of(), List.of(), List.of()),
                        null,
                        null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void foreignMediaIsRejectedByCompanyScopedMediaLookup() {
    Fixture fixture = fixture(true);
    when(fixture.mediaService.requireActiveCompanyMedia(999L, 10L))
        .thenThrow(new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));

    assertThatThrownBy(
            () ->
                fixture.service.send(
                    10L,
                    40L,
                    new MetaWhatsAppSendRequest(
                        "request-3",
                        "MEDIA",
                        null,
                        null,
                        null,
                        null,
                        "999",
                        "caption")))
        .isInstanceOf(MediaException.class);
  }

  private Fixture fixture(boolean conversationVisible) {
    InboxConversationRepository conversationRepository =
        mock(InboxConversationRepository.class);
    ChannelConnection connection = new ChannelConnection();
    connection.setId(20L);
    connection.setCompanyId(10L);
    connection.setChannelType(ChannelType.WHATSAPP);
    connection.setProviderType(ProviderType.META_WHATSAPP);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection.setCreatedByUserId(7L);

    InboxConversation conversation = new InboxConversation();
    conversation.setId(40L);
    conversation.setCompanyId(10L);
    conversation.setConnection(connection);
    conversation.setProviderType(ProviderType.META_WHATSAPP);
    conversation.setExternalConversationId("wa:15550001111");
    if (conversationVisible) {
      when(conversationRepository.findByIdAndCompanyId(40L, 10L))
          .thenReturn(Optional.of(conversation));
    }

    MetaWhatsAppControlPlaneProjectionRepository controlPlane =
        mock(MetaWhatsAppControlPlaneProjectionRepository.class);
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

    MetaWhatsAppCredentialResolver credentials =
        mock(MetaWhatsAppCredentialResolver.class);
    when(credentials.require(20L))
        .thenReturn(
            new MetaWhatsAppCredentialResolver.StoredCredential(
                "token", "waba-1", "phone-1", "v24.0"));

    MetaWhatsAppTemplateService templateService =
        mock(MetaWhatsAppTemplateService.class);
    MediaService mediaService = mock(MediaService.class);
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();

    MetaWhatsAppMessageService service =
        new MetaWhatsAppMessageService(
            conversationRepository,
            mock(InboxMessageRepository.class),
            controlPlane,
            credentials,
            mock(MetaWhatsAppRuntimeGraphClient.class),
            new MetaWhatsAppCustomerServicePolicy(properties),
            templateService,
            mock(MetaWhatsAppOutboundReservationStore.class),
            mock(MetaWhatsAppRealtimePublisher.class),
            mediaService,
            mock(MessageStorageMeter.class),
            mock(StorageQuotaService.class),
            new SessionJdbcTemplate(),
            mock(TransactionTemplate.class));

    return new Fixture(service, templateService, mediaService);
  }

  private record Fixture(
      MetaWhatsAppMessageService service,
      MetaWhatsAppTemplateService templateService,
      MediaService mediaService) {}

  private static final class SessionJdbcTemplate extends JdbcTemplate {
    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      try {
        @SuppressWarnings("unchecked")
        T value = (T) Instant.now();
        return List.of(value);
      } catch (RuntimeException exception) {
        throw exception;
      }
    }
  }
}
