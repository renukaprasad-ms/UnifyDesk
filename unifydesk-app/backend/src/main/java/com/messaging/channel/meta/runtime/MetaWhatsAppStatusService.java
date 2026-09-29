package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.inbox.enums.InboxMessageStatus;
import com.messaging.inbox.repository.InboxMessageRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppStatusService {
  private final ObjectMapper objectMapper;
  private final MetaWhatsAppTenantResolver tenantResolver;
  private final JdbcTemplate jdbcTemplate;
  private final InboxMessageRepository messageRepository;
  private final MetaWhatsAppRealtimePublisher realtimePublisher;
  private final MetaWhatsAppMessageStateMachine stateMachine;

  @Transactional
  public boolean apply(MetaWhatsAppWebhookEventStore.ClaimedEvent event) {
    JsonNode root = read(event.payloadJson());
    JsonNode status = root.path("status");
    String phoneNumberId = text(root, "phoneNumberId");
    String providerMessageId = text(status, "id");
    String providerStatus = text(status, "status");
    if (phoneNumberId == null || providerMessageId == null || providerStatus == null) return false;

    var tenant = tenantResolver.resolveTrustedPhone(phoneNumberId);
    List<Row> rows =
        jdbcTemplate.query(
            """
            select id,inbox_message_id,detailed_status,status_rank,provider_status_at
            from meta_whatsapp_message_runtime
            where connection_id=? and provider_message_id=?
            for update
            """,
            (rs, rowNum) ->
                new Row(
                    rs.getLong("id"),
                    rs.getObject("inbox_message_id", Long.class),
                    rs.getString("detailed_status"),
                    rs.getInt("status_rank"),
                    rs.getTimestamp("provider_status_at") == null
                        ? null
                        : rs.getTimestamp("provider_status_at").toInstant()),
            tenant.connectionId(),
            providerMessageId);
    if (rows.isEmpty()) return false;

    Row current = rows.getFirst();
    MetaWhatsAppMessageStateMachine.State next = stateMachine.providerState(providerStatus);
    if (!stateMachine.shouldApply(
        current.status(),
        current.rank(),
        current.providerStatusAt(),
        next,
        event.providerEventAt())) {
      return true;
    }

    Integer code = firstInt(status.path("errors"), "code");
    Integer subcode = firstInt(status.path("errors"), "error_subcode");
    String trace = firstText(status.path("errors"), "fbtrace_id");
    jdbcTemplate.update(
        """
        update meta_whatsapp_message_runtime
        set detailed_status=?,status_rank=?,provider_status_at=?,
            last_provider_error_code=?,last_provider_error_subcode=?,last_provider_fbtrace_id=?,
            updated_at=now()
        where id=?
        """,
        next.status(),
        next.rank(),
        Timestamp.from(event.providerEventAt()),
        code,
        subcode,
        trace,
        current.id());

    if (current.inboxMessageId() != null) {
      messageRepository
          .findById(current.inboxMessageId())
          .ifPresent(
              message -> {
                message.setStatus(
                    next.status().equals("FAILED")
                        ? InboxMessageStatus.FAILED
                        : InboxMessageStatus.SENT);
                messageRepository.save(message);
                scheduleRealtime(
                    tenant.companyId(), tenant.connectionId(), message.getConversation().getId(), message.getId());
              });
    }
    return true;
  }

  private Integer firstInt(JsonNode errors, String field) {
    if (!errors.isArray() || errors.isEmpty()) return null;
    JsonNode value = errors.get(0).path(field);
    return value.canConvertToInt() ? value.asInt() : null;
  }

  private String firstText(JsonNode errors, String field) {
    if (!errors.isArray() || errors.isEmpty()) return null;
    return text(errors.get(0), field);
  }

  private JsonNode read(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception exception) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_WEBHOOK_INVALID_STATUS", false, "Stored status payload is invalid", exception);
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode value = node.get(field);
    return value == null || !value.isValueNode() || value.asText().isBlank()
        ? null
        : value.asText().trim();
  }

  private void scheduleRealtime(
      long companyId, long connectionId, long conversationId, long messageId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            realtimePublisher.statusChanged(
                companyId, connectionId, conversationId, messageId);
          }
        });
  }

  private record Row(
      long id, Long inboxMessageId, String status, int rank, Instant providerStatusAt) {}
}
