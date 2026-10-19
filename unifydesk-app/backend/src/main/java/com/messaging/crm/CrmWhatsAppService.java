package com.messaging.crm;

import com.messaging.common.validation.ValidPhone;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.MetaWhatsAppCustomerServicePolicy;
import com.messaging.channel.meta.runtime.MetaWhatsAppMessageService;
import com.messaging.channel.meta.runtime.MetaWhatsAppTemplateService;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendResponse;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.repository.InboxConversationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmWhatsAppService {

  private final ChannelConnectionRepository connectionRepository;
  private final InboxConversationRepository conversationRepository;
  private final MetaWhatsAppMessageService messageService;
  private final MetaWhatsAppTemplateService templateService;
  private final MetaWhatsAppCustomerServicePolicy customerServicePolicy;
  private final JdbcTemplate jdbc;

  public record WhatsAppContext(
      boolean connected,
      String connectionId,
      String connectionName,
      List<String> phoneNumbers,
      String primaryPhone,
      String conversationId,
      boolean freeFormAllowed,
      Instant sessionExpiresAt) {}

  public record SendInput(
      Long contactId,
      Long dealId,
      @ValidPhone String phone,
      Long connectionId,
      String type,
      String templateId,
      MetaWhatsAppSendRequest.Parameters parameters,
      String text) {}

  public record SendResult(
      boolean success,
      String messageId,
      String conversationId,
      String status,
      String recipientPhone) {}

  @Transactional(readOnly = true)
  public WhatsAppContext context(long companyId, Long contactId) {
    ChannelConnection connection = findActiveConnection(companyId, null);
    if (connection == null) {
      return new WhatsAppContext(false, null, null, List.of(), null, null, false, null);
    }

    List<String> phones = List.of();
    String primaryPhone = null;
    if (contactId != null) {
      phones =
          jdbc.query(
              """
              select coalesce(nullif(btrim(value), ''), normalized_value) as phone
              from contact_identities
              where company_id = ? and contact_id = ? and identity_type = 'PHONE' and status = 'ACTIVE'
              order by is_primary desc, id asc
              """,
              (rs, row) -> rs.getString("phone"),
              companyId,
              contactId);
      if (!phones.isEmpty()) {
        primaryPhone = phones.getFirst();
      }
    }

    String conversationId = null;
    boolean freeForm = false;
    Instant sessionExpiresAt = null;

    if (primaryPhone != null) {
      String clean = cleanPhone(primaryPhone);
      String externalConversationId = "wa:" + clean;
      var found =
          conversationRepository.findByConnectionIdAndExternalConversationId(
              connection.getId(), externalConversationId);
      if (found.isPresent()) {
        conversationId = String.valueOf(found.get().getId());
        Instant lastCustomerMessageAt =
            jdbc.query(
                """
                select max(sent_at) as last_at from inbox_messages
                where connection_id = ? and from_address = ? and direction = 'INBOUND'
                """,
                rs -> rs.next() && rs.getTimestamp("last_at") != null
                    ? rs.getTimestamp("last_at").toInstant()
                    : null,
                connection.getId(),
                clean);
        freeForm = customerServicePolicy.freeFormAllowed(lastCustomerMessageAt, Instant.now());
        sessionExpiresAt = customerServicePolicy.expiresAt(lastCustomerMessageAt);
      }
    }

    return new WhatsAppContext(
        true,
        String.valueOf(connection.getId()),
        connection.getName(),
        phones,
        primaryPhone,
        conversationId,
        freeForm,
        sessionExpiresAt);
  }

  @Transactional(readOnly = true)
  public List<MetaWhatsAppTemplateResponse> templates(long companyId, Long connectionId) {
    ChannelConnection connection = requireActiveConnection(companyId, connectionId);
    List<MetaWhatsAppTemplateResponse> list = templateService.list(companyId, connection.getId());
    return list.stream()
        .filter(t -> "APPROVED".equalsIgnoreCase(t.status()))
        .toList();
  }

  @Transactional
  public SendResult send(long companyId, SendInput input, long actorUserId) {
    ChannelConnection connection = requireActiveConnection(companyId, input.connectionId());

    String rawPhone = input.phone();
    if ((rawPhone == null || rawPhone.isBlank()) && input.contactId() != null) {
      List<String> found =
          jdbc.query(
              """
              select coalesce(nullif(btrim(value), ''), normalized_value) as phone
              from contact_identities
              where company_id = ? and contact_id = ? and identity_type = 'PHONE' and status = 'ACTIVE'
              order by is_primary desc, id asc limit 1
              """,
              (rs, row) -> rs.getString("phone"),
              companyId,
              input.contactId());
      if (!found.isEmpty()) {
        rawPhone = found.getFirst();
      }
    }

    if (rawPhone == null || rawPhone.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_WHATSAPP_PHONE_REQUIRED");
    }

    String clean = cleanPhone(rawPhone);
    if (clean.length() < 7 || clean.length() > 15) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_WHATSAPP_INVALID_PHONE");
    }

    InboxConversation conversation =
        getOrCreateConversation(companyId, connection, input.contactId(), clean);

    String type = input.type() != null ? input.type().trim().toUpperCase(Locale.ROOT) : "TEMPLATE";
    String clientRequestId = "crm-" + UUID.randomUUID();

    MetaWhatsAppSendRequest metaRequest =
        new MetaWhatsAppSendRequest(
            clientRequestId,
            type,
            input.text(),
            null,
            input.templateId(),
            input.parameters(),
            null,
            null);

    MetaWhatsAppSendResponse response =
        messageService.send(companyId, conversation.getId(), metaRequest);

    // Record activity in CRM
    String preview =
        "TEMPLATE".equals(type)
            ? "Template: " + input.templateId()
            : (input.text() != null && input.text().length() > 60
                ? input.text().substring(0, 57) + "..."
                : String.valueOf(input.text()));

    if (input.contactId() != null) {
      jdbc.update(
          """
          insert into crm_notes (id, company_id, contact_id, body, actor_user_id)
          values (?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          companyId,
          input.contactId(),
          "Sent WhatsApp message (" + preview + ") to +" + clean,
          actorUserId);
    }

    if (input.dealId() != null) {
      jdbc.update(
          """
          insert into crm_deal_events (id, company_id, deal_id, event_type, new_stage, changed_fields, actor_user_id)
          values (?, ?, ?, 'UPDATED',
            (select stage from crm_deals where company_id = ? and id = ?),
            ?, ?)
          """,
          IdGenerator.nextId(),
          companyId,
          input.dealId(),
          companyId,
          input.dealId(),
          "WhatsApp sent: " + preview,
          actorUserId);
    }

    return new SendResult(
        true,
        response.messageId(),
        String.valueOf(conversation.getId()),
        response.status(),
        clean);
  }

  private InboxConversation getOrCreateConversation(
      long companyId, ChannelConnection connection, Long contactId, String cleanPhone) {
    String externalConversationId = "wa:" + cleanPhone;
    var existing =
        conversationRepository.findByConnectionIdAndExternalConversationId(
            connection.getId(), externalConversationId);
    if (existing.isPresent()) {
      return existing.get();
    }

    String contactName = null;
    if (contactId != null) {
      List<String> names =
          jdbc.query(
              "select display_name from contacts where company_id = ? and id = ?",
              (rs, row) -> rs.getString("display_name"),
              companyId,
              contactId);
      if (!names.isEmpty()) contactName = names.getFirst();
    }
    if (contactName == null || contactName.isBlank()) {
      contactName = "+" + cleanPhone;
    }

    long candidateId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());

    jdbc.update(
        """
        insert into inbox_conversations
          (id, company_id, connection_id, provider_type, external_conversation_id,
           last_message_preview, last_message_at, unread_count, status, created_at, updated_at)
        values (?, ?, ?, 'META_WHATSAPP', ?, 'Started from CRM', ?, 0, 'OPEN', ?, ?)
        on conflict (connection_id, external_conversation_id) do nothing
        """,
        candidateId,
        companyId,
        connection.getId(),
        externalConversationId,
        now,
        now,
        now);

    InboxConversation conversation =
        conversationRepository
            .findByConnectionIdAndExternalConversationId(connection.getId(), externalConversationId)
            .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONVERSATION_CREATE_FAILED"));

    jdbc.update(
        """
        insert into inbox_participants
          (id, conversation_id, participant_type, address, display_name, created_at, updated_at)
        values (?, ?, 'PHONE_NUMBER', ?, ?, ?, ?)
        on conflict (conversation_id, participant_type, address)
        do update set display_name = coalesce(excluded.display_name, inbox_participants.display_name),
                      updated_at = excluded.updated_at
        """,
        IdGenerator.nextId(),
        conversation.getId(),
        cleanPhone,
        contactName,
        now,
        now);

    if (contactId != null) {
      jdbc.update(
          """
          insert into inbox_participant_contact_links
            (id, company_id, conversation_id, participant_address, identity_type, contact_id, resolution, created_at, updated_at)
          values (?, ?, ?, ?, 'PHONE', ?, 'MATCHED', ?, ?)
          on conflict do nothing
          """,
          IdGenerator.nextId(),
          companyId,
          conversation.getId(),
          cleanPhone,
          contactId,
          now,
          now);
    }

    return conversation;
  }

  private ChannelConnection requireActiveConnection(long companyId, Long connectionId) {
    ChannelConnection connection = findActiveConnection(companyId, connectionId);
    if (connection == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_WHATSAPP_CONNECTION");
    }
    return connection;
  }

  private ChannelConnection findActiveConnection(long companyId, Long connectionId) {
    if (connectionId != null) {
      return connectionRepository
          .findByIdAndCompanyId(connectionId, companyId)
          .filter(c -> c.getStatus() == ChannelConnectionStatus.ACTIVE
              && c.getProviderType() == ProviderType.META_WHATSAPP)
          .orElse(null);
    }
    List<ChannelConnection> connections =
        connectionRepository.findByCompanyIdAndChannelTypeOrderByCreatedAtDesc(
            companyId, ChannelType.WHATSAPP);
    return connections.stream()
        .filter(c -> c.getStatus() == ChannelConnectionStatus.ACTIVE
            && c.getProviderType() == ProviderType.META_WHATSAPP)
        .findFirst()
        .orElse(null);
  }

  private static String cleanPhone(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("[^0-9]", "");
  }
}
