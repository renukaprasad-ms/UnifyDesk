package com.messaging.notification.platform.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.repository.NotificationEventRepository;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationEventService {

  private final NotificationEventRepository repository;
  private final ObjectMapper objectMapper;
  private final NotificationTypeRegistry registry;

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordCompanyCreated(long companyId, long creatorUserId, String companyName) {
    return record(
        "company.created:" + companyId,
        "company.created",
        companyId,
        creatorUserId,
        "USER",
        creatorUserId,
        null,
        "COMPANY",
        Long.toString(companyId),
        null,
        Map.of("companyName", companyName),
        Instant.now());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordCompanyInvitation(
      long invitationId,
      long companyId,
      long actorUserId,
      long recipientUserId,
      String companyName) {
    return record(
        "company.invitation.created:" + invitationId,
        "company.invitation.created",
        null,
        actorUserId,
        "USER",
        recipientUserId,
        null,
        "COMPANY_INVITATION",
        Long.toString(invitationId),
        null,
        Map.of("companyName", companyName),
        Instant.now());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordCompanyInvitationAccepted(
      long invitationId,
      long companyId,
      long actorUserId,
      long recipientUserId,
      String companyName,
      String memberName) {
    return record(
        "company.invitation.accepted:" + invitationId,
        "company.invitation.accepted",
        companyId,
        actorUserId,
        "USER",
        recipientUserId,
        null,
        "COMPANY_INVITATION",
        Long.toString(invitationId),
        null,
        Map.of("companyName", companyName, "memberName", memberName),
        Instant.now());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordInboxActivity(
      long messageId,
      long companyId,
      long connectionId,
      String sender,
      String subject,
      String snippet,
      String inboxName,
      Instant occurredAt) {
    return record(
        "inbox.message.received:" + messageId,
        "inbox.activity",
        companyId,
        null,
        "COMPANY_PERMISSION",
        null,
        "email.read",
        "INBOX_MESSAGE",
        Long.toString(messageId),
        "inbox:" + connectionId,
        Map.of(
            "connectionId", Long.toString(connectionId),
            "sender", sender,
            "subject", subject,
            "snippet", snippet,
            "inboxName", inboxName),
        occurredAt == null ? Instant.now() : occurredAt);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordBroadcastCompleted(
      long runId, long broadcastId, long companyId, String channel, Instant occurredAt) {
    return record(
        "broadcast.completed:" + runId,
        "broadcast.completed",
        companyId,
        null,
        "COMPANY_PERMISSION",
        null,
        "broadcast.read",
        "BROADCAST_RUN",
        Long.toString(runId),
        null,
        Map.of(
            "runId", Long.toString(runId),
            "broadcastId", Long.toString(broadcastId),
            "channel", channel),
        occurredAt == null ? Instant.now() : occurredAt);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordBroadcastFailed(
      long runId, long broadcastId, long companyId, String channel, Instant occurredAt) {
    return record(
        "broadcast.failed:" + runId,
        "broadcast.failed",
        companyId,
        null,
        "COMPANY_PERMISSION",
        null,
        "broadcast.read",
        "BROADCAST_RUN",
        Long.toString(runId),
        null,
        Map.of(
            "runId", Long.toString(runId),
            "broadcastId", Long.toString(broadcastId),
            "channel", channel),
        occurredAt == null ? Instant.now() : occurredAt);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long recordCrmTaskDue(
      long taskId, long companyId, long assigneeId, long generation, String taskTitle) {
    String safeTitle =
        taskTitle == null
            ? "Customer follow-up"
            : taskTitle.replaceAll("[\\p{Cntrl}]+", " ").trim();
    if (safeTitle.isBlank()) safeTitle = "Customer follow-up";
    if (safeTitle.length() > 160) safeTitle = safeTitle.substring(0, 160).trim();
    return record(
        "crm.task.due:" + taskId + ":" + generation,
        "crm.task.due",
        companyId,
        null,
        "USER",
        assigneeId,
        "crm.task.read",
        "CRM_TASK",
        Long.toString(taskId),
        null,
        Map.of(
            "taskId",
            Long.toString(taskId),
            "taskTitle",
            safeTitle,
            "generation",
            Long.toString(generation)),
        Instant.now());
  }

  private long record(
      String eventKey,
      String eventType,
      Long companyId,
      Long actorUserId,
      String recipientMode,
      Long recipientUserId,
      String permission,
      String subjectType,
      String subjectId,
      String groupKey,
      Map<String, String> variables,
      Instant occurredAt) {
    NotificationTypeRegistry.Definition definition = registry.requireSource(eventType, 1);
    registry.validateVariables(definition, variables);
    String json = json(variables);
    if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024) {
      throw new BadRequestException("Notification variables too large");
    }
    return repository.insertPending(
        eventKey,
        eventType,
        companyId,
        actorUserId,
        recipientMode,
        recipientUserId,
        permission,
        subjectType,
        subjectId,
        groupKey,
        json,
        occurredAt);
  }

  private String json(Map<String, String> variables) {
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Invalid notification variables");
    }
  }
}
