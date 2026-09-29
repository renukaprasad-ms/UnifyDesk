package com.messaging.channel.meta.runtime;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppComposerResponse;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.id.IdGenerator;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.entity.Media;
import com.messaging.media.service.MediaService;
import com.messaging.realtime.service.RealtimeService;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.MessageStorageMeter;
import com.messaging.storage.service.StorageQuotaService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppMessageService {
  private final InboxConversationRepository conversationRepository;
  private final InboxMessageRepository messageRepository;
  private final MetaWhatsAppControlPlaneProjectionRepository controlPlane;
  private final MetaWhatsAppCredentialResolver credentialResolver;
  private final MetaWhatsAppRuntimeGraphClient graphClient;
  private final MetaWhatsAppRuntimeProperties properties;
  private final MetaWhatsAppCustomerServicePolicy customerServicePolicy;
  private final MetaWhatsAppTemplateService templateService;
  private final MediaService mediaService;
  private final MessageStorageMeter messageStorageMeter;
  private final StorageQuotaService storageQuotaService;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final RealtimeService realtimeService;

  public MetaWhatsAppSendResponse send(
      long companyId, long conversationId, MetaWhatsAppSendRequest request) {
    InboxConversation conversation = requireConversation(companyId, conversationId);
    SendContext context = requireSendContext(companyId, conversation);
    String type = request.type().trim().toUpperCase(Locale.ROOT);
    String replyTo = resolveReply(companyId, conversationId, request.replyToMessageId());

    MetaWhatsAppTemplateService.PreparedTemplate preparedTemplate = null;
    Media media = null;
    String body;
    if ("TEXT".equals(type)) {
      if (!StringUtils.hasText(request.text())) {
        throw new BadRequestException("WhatsApp text is required");
      }
      requireFreeForm(context);
      body = request.text().trim();
    } else if ("TEMPLATE".equals(type)) {
      if (!StringUtils.hasText(request.templateId())) {
        throw new BadRequestException("WhatsApp templateId is required");
      }
      preparedTemplate =
          templateService.prepare(
              companyId, context.connection().getId(), request.templateId(), request.parameters());
      body = "Template: " + preparedTemplate.name();
    } else if ("MEDIA".equals(type)) {
      if (!StringUtils.hasText(request.mediaId())) {
        throw new BadRequestException("WhatsApp mediaId is required");
      }
      requireFreeForm(context);
      media = mediaService.requireActiveCompanyMedia(parseId(request.mediaId(), "mediaId"), companyId);
      if (!supportedMime(media.getContentType())) {
        throw new BadRequestException("WHATSAPP_MEDIA_UNSUPPORTED");
      }
      body = StringUtils.hasText(request.caption()) ? request.caption().trim() : media.getOriginalFileName();
    } else {
      throw new BadRequestException("Unsupported WhatsApp message type");
    }

    MetaWhatsAppSendResponse duplicate =
        existingResponse(companyId, context.connection().getId(), request.clientRequestId());
    if (duplicate != null) return duplicate;

    Reservation reservation =
        reserve(
            companyId,
            conversation,
            context,
            request.clientRequestId(),
            type,
            body);
    if (!reservation.created()) {
      return existingResponse(companyId, context.connection().getId(), request.clientRequestId());
    }

    try {
      MetaWhatsAppRuntimeGraphClient.SendResult result;
      if ("TEXT".equals(type)) {
        result =
            graphClient.sendText(
                context.credential(), context.customerKey(), request.text().trim(), replyTo);
      } else if ("TEMPLATE".equals(type)) {
        result =
            graphClient.sendTemplate(
                context.credential(),
                context.customerKey(),
                preparedTemplate.name(),
                preparedTemplate.language(),
                preparedTemplate.providerComponents());
      } else {
        byte[] content = mediaService.readTrustedObject(media.getId(), companyId);
        String providerMediaId =
            graphClient.uploadMedia(
                context.credential(),
                content,
                media.getOriginalFileName(),
                media.getContentType());
        linkOutboundMedia(reservation.messageId(), providerMediaId, media.getId());
        result =
            graphClient.sendMedia(
                context.credential(),
                context.customerKey(),
                mediaType(media.getContentType()),
                providerMediaId,
                request.caption(),
                replyTo);
      }
      return markAccepted(
          companyId,
          context.connection().getId(),
          request.clientRequestId(),
          reservation.messageId(),
          result.providerMessageId());
    } catch (MetaWhatsAppProviderException exception) {
      if (exception.ambiguousOutcome()) {
        return markUnknown(
            companyId,
            context.connection().getId(),
            request.clientRequestId(),
            reservation.messageId(),
            exception);
      }
      markFailed(
          companyId,
          context.connection().getId(),
          request.clientRequestId(),
          reservation.messageId(),
          exception);
      if (exception.retryable()) {
        throw new ServiceUnavailableException(exception.safeCategory(), exception);
      }
      throw new BadRequestException(exception.safeCategory());
    }
  }

  public MetaWhatsAppComposerResponse composer(long companyId, long conversationId) {
    InboxConversation conversation = requireConversation(companyId, conversationId);
    SendContext context = requireSendContext(companyId, conversation);
    Instant lastCustomerMessageAt =
        lastCustomerMessageAt(context.connection().getId(), context.customerKey());
    Instant expiresAt = customerServicePolicy.expiresAt(lastCustomerMessageAt);
    boolean freeForm = customerServicePolicy.freeFormAllowed(lastCustomerMessageAt, Instant.now());
    return new MetaWhatsAppComposerResponse(
        freeForm,
        expiresAt,
        !freeForm,
        List.of("IMAGE", "DOCUMENT"),
        context.connection().getStatus().name());
  }

  private Reservation reserve(
      long companyId,
      InboxConversation conversation,
      SendContext context,
      String clientRequestId,
      String type,
      String body) {
    return transactionTemplate.execute(
        ignored -> {
          long runtimeId = IdGenerator.nextId();
          Instant now = Instant.now();
          int inserted =
              jdbcTemplate.update(
                  """
                  insert into meta_whatsapp_message_runtime
                    (id,company_id,connection_id,client_request_id,direction,message_kind,
                     detailed_status,status_rank,created_at,updated_at)
                  values (?,?,?,?,'OUTBOUND',?,'PENDING',0,?,?)
                  on conflict (company_id,connection_id,client_request_id)
                    where client_request_id is not null
                  do nothing
                  """,
                  runtimeId,
                  companyId,
                  context.connection().getId(),
                  clientRequestId,
                  type,
                  Timestamp.from(now),
                  Timestamp.from(now));
          if (inserted == 0) return new Reservation(0, false);

          InboxMessage message = new InboxMessage();
          message.setConversation(conversation);
          message.setConnection(context.connection());
          message.setExternalMessageId("client:" + clientRequestId);
          message.setProviderFolderKey("WHATSAPP");
          message.setFromAddress(context.credential().phoneNumberId());
          message.setToAddresses(context.customerKey());
          message.setDirection(InboxMessageDirection.OUTBOUND);
          message.setStatus(InboxMessageStatus.DRAFT);
          message.setSnippet(compact(body, 500));
          message.setBodyText(body);
          message.setSentAt(now);
          message.setRead(true);
          message.setStorageBytes(
              messageStorageMeter.measure(
                  message.getExternalMessageId(), null, null, null, message.getSnippet(), body, null));
          message = messageRepository.saveAndFlush(message);
          storageQuotaService.consume(
              companyId,
              StorageUsageType.MESSAGE,
              "INBOX_MESSAGE",
              message.getId().toString(),
              message.getStorageBytes());
          jdbcTemplate.update(
              "update meta_whatsapp_message_runtime set inbox_message_id=? where id=?",
              message.getId(),
              runtimeId);
          conversation.setLastMessageAt(now);
          conversation.setLastMessagePreview(message.getSnippet());
          conversationRepository.save(conversation);
          return new Reservation(message.getId(), true);
        });
  }

  private void linkOutboundMedia(
      long messageId, String providerMediaId, long mediaId) {
    jdbcTemplate.update(
        """
        insert into meta_whatsapp_message_media
          (id,message_id,provider_media_id,media_id,direction,created_at)
        values (?,?,?,?,'OUTBOUND',now())
        on conflict (message_id,provider_media_id) do nothing
        """,
        IdGenerator.nextId(),
        messageId,
        providerMediaId,
        mediaId);
  }

  private MetaWhatsAppSendResponse markAccepted(
      long companyId,
      long connectionId,
      String clientRequestId,
      long messageId,
      String providerMessageId) {
    Instant acceptedAt = Instant.now();
    transactionTemplate.executeWithoutResult(
        ignored -> {
          jdbcTemplate.update(
              """
              update meta_whatsapp_message_runtime
              set provider_message_id=?,detailed_status='ACCEPTED',status_rank=10,
                  provider_status_at=?,safe_error_code=null,updated_at=?
              where company_id=? and connection_id=? and client_request_id=?
              """,
              providerMessageId,
              Timestamp.from(acceptedAt),
              Timestamp.from(acceptedAt),
              companyId,
              connectionId,
              clientRequestId);
          messageRepository
              .findById(messageId)
              .ifPresent(
                  message -> {
                    message.setExternalMessageId(providerMessageId);
                    message.setStatus(InboxMessageStatus.SENT);
                    message.setSentAt(acceptedAt);
                    messageRepository.save(message);
                  });
        });
    publishMessageChanged(companyId, connectionId, messageId);
    return new MetaWhatsAppSendResponse(
        String.valueOf(messageId),
        clientRequestId,
        providerMessageId,
        "ACCEPTED",
        acceptedAt,
        null);
  }

  private MetaWhatsAppSendResponse markUnknown(
      long companyId,
      long connectionId,
      String clientRequestId,
      long messageId,
      MetaWhatsAppProviderException exception) {
    Instant now = Instant.now();
    updateOutcome(
        companyId,
        connectionId,
        clientRequestId,
        messageId,
        "UNKNOWN",
        5,
        exception,
        false,
        now);
    return new MetaWhatsAppSendResponse(
        String.valueOf(messageId),
        clientRequestId,
        null,
        "UNKNOWN",
        null,
        exception.safeCategory());
  }

  private void markFailed(
      long companyId,
      long connectionId,
      String clientRequestId,
      long messageId,
      MetaWhatsAppProviderException exception) {
    updateOutcome(
        companyId,
        connectionId,
        clientRequestId,
        messageId,
        "FAILED",
        25,
        exception,
        true,
        Instant.now());
  }

  private void updateOutcome(
      long companyId,
      long connectionId,
      String clientRequestId,
      long messageId,
      String status,
      int rank,
      MetaWhatsAppProviderException exception,
      boolean failedInbox,
      Instant at) {
    transactionTemplate.executeWithoutResult(
        ignored -> {
          jdbcTemplate.update(
              """
              update meta_whatsapp_message_runtime
              set detailed_status=?,status_rank=?,provider_status_at=?,
                  provider_error_code=?,provider_error_subcode=?,provider_fbtrace_id=?,
                  safe_error_code=?,updated_at=?
              where company_id=? and connection_id=? and client_request_id=?
              """,
              status,
              rank,
              Timestamp.from(at),
              exception.metaCode(),
              exception.metaSubcode(),
              exception.fbtraceId(),
              exception.safeCategory(),
              Timestamp.from(at),
              companyId,
              connectionId,
              clientRequestId);
          if (failedInbox) {
            messageRepository
                .findById(messageId)
                .ifPresent(
                    message -> {
                      message.setStatus(InboxMessageStatus.FAILED);
                      messageRepository.save(message);
                    });
          }
        });
    publishMessageChanged(companyId, connectionId, messageId);
  }

  private MetaWhatsAppSendResponse existingResponse(
      long companyId, long connectionId, String clientRequestId) {
    List<MetaWhatsAppSendResponse> rows =
        jdbcTemplate.query(
            """
            select inbox_message_id,client_request_id,provider_message_id,detailed_status,
                   provider_status_at,safe_error_code
            from meta_whatsapp_message_runtime
            where company_id=? and connection_id=? and client_request_id=?
            """,
            (rs, rowNum) ->
                new MetaWhatsAppSendResponse(
                    String.valueOf(rs.getLong("inbox_message_id")),
                    rs.getString("client_request_id"),
                    rs.getString("provider_message_id"),
                    rs.getString("detailed_status"),
                    rs.getTimestamp("provider_status_at") == null
                        ? null
                        : rs.getTimestamp("provider_status_at").toInstant(),
                    rs.getString("safe_error_code")),
            companyId,
            connectionId,
            clientRequestId);
    return rows.stream().findFirst().orElse(null);
  }

  private SendContext requireSendContext(long companyId, InboxConversation conversation) {
    ChannelConnection connection = conversation.getConnection();
    if (connection.getProviderType() != ProviderType.META_WHATSAPP
        || connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new NotFoundException("WhatsApp conversation not found");
    }
    var projection =
        controlPlane
            .findByConnectionId(connection.getId())
            .orElseThrow(() -> new ServiceUnavailableException("WHATSAPP_CONNECTION_NOT_READY"));
    if (projection.companyId() != companyId || !projection.usableForSend()) {
      throw new ServiceUnavailableException("WHATSAPP_CONNECTION_NOT_READY");
    }
    var credential = credentialResolver.require(connection.getId());
    if (!credential.phoneNumberId().equals(projection.selectedPhoneNumberId())) {
      throw new ServiceUnavailableException("WHATSAPP_CONNECTION_NOT_READY");
    }
    String external = conversation.getExternalConversationId();
    if (external == null || !external.startsWith("wa:") || external.length() <= 3) {
      throw new NotFoundException("WhatsApp conversation not found");
    }
    return new SendContext(connection, credential, external.substring(3));
  }

  private InboxConversation requireConversation(long companyId, long conversationId) {
    return conversationRepository
        .findByIdAndCompanyId(conversationId, companyId)
        .orElseThrow(() -> new NotFoundException("WhatsApp conversation not found"));
  }

  private void requireFreeForm(SendContext context) {
    Instant lastCustomerMessageAt =
        lastCustomerMessageAt(context.connection().getId(), context.customerKey());
    if (!customerServicePolicy.freeFormAllowed(lastCustomerMessageAt, Instant.now())) {
      throw new ConflictException("WHATSAPP_TEMPLATE_REQUIRED");
    }
  }

  private Instant lastCustomerMessageAt(long connectionId, String customerKey) {
    List<Instant> rows =
        jdbcTemplate.query(
            """
            select last_customer_message_at
            from meta_whatsapp_customer_sessions
            where connection_id=? and customer_key=?
            """,
            (rs, rowNum) -> rs.getTimestamp(1).toInstant(),
            connectionId,
            customerKey);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private String resolveReply(
      long companyId, long conversationId, String replyToMessageId) {
    if (!StringUtils.hasText(replyToMessageId)) return null;
    long id = parseId(replyToMessageId, "replyToMessageId");
    InboxMessage message =
        messageRepository
            .findByIdAndCompanyId(id, companyId)
            .filter(item -> item.getConversation().getId().equals(conversationId))
            .orElseThrow(() -> new NotFoundException("Reply message not found"));
    String external = message.getExternalMessageId();
    if (!StringUtils.hasText(external) || external.startsWith("client:")) {
      throw new ConflictException("WHATSAPP_REPLY_TARGET_NOT_READY");
    }
    return external;
  }

  private long parseId(String value, String field) {
    try {
      return Long.parseLong(value);
    } catch (Exception exception) {
      throw new BadRequestException("Invalid " + field);
    }
  }

  private boolean supportedMime(String mime) {
    if (mime == null) return false;
    String normalized = mime.toLowerCase(Locale.ROOT);
    return normalized.equals("image/jpeg")
        || normalized.equals("image/png")
        || normalized.equals("image/webp")
        || normalized.equals("application/pdf");
  }

  private String mediaType(String mime) {
    return "application/pdf".equalsIgnoreCase(mime) ? "document" : "image";
  }

  private String compact(String value, int max) {
    if (value == null) return null;
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  private void publishMessageChanged(long companyId, long connectionId, long messageId) {
    try {
      messageRepository
          .findById(messageId)
          .ifPresent(
              message ->
                  realtimeService.sendToCompany(
                      companyId,
                      "inbox.email.changed",
                      com.messaging.inbox.dto.InboxEmailChangedEvent.delta(
                          connectionId,
                          ProviderType.META_WHATSAPP,
                          List.of(message.getConversation().getId()),
                          List.of(messageId),
                          List.of())));
    } catch (RuntimeException ignored) {
      // Durable state is authoritative if realtime fanout is unavailable.
    }
  }

  private record Reservation(long messageId, boolean created) {}
  private record SendContext(
      ChannelConnection connection,
      MetaWhatsAppCredentialResolver.StoredCredential credential,
      String customerKey) {}
}
