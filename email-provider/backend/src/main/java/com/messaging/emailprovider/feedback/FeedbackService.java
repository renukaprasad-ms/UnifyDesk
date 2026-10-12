package com.messaging.emailprovider.feedback;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.feedback.dto.FeedbackResultResponse;
import com.messaging.emailprovider.feedback.dto.PostfixFeedbackRequest;
import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import com.messaging.emailprovider.queue.EmailOutboxService;
import com.messaging.emailprovider.reputation.ReputationService;
import com.messaging.emailprovider.suppression.SuppressionReason;
import com.messaging.emailprovider.suppression.SuppressionService;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

  private final EmailRecipientRepository recipientRepository;
  private final EmailMessageRepository messageRepository;
  private final EmailEventRepository eventRepository;
  private final SuppressionService suppressionService;
  private final ReputationService reputationService;
  private final EmailOutboxService outboxService;
  private final LifecycleEventEncoder eventEncoder;
  private final com.messaging.emailprovider.delivery.EmailMessageStateService messageStateService;
  private final String lifecycleTopic;
  private final com.messaging.emailprovider.product.webhook.WebhookService webhooks;

  public FeedbackService(
      EmailRecipientRepository recipientRepository,
      EmailMessageRepository messageRepository,
      EmailEventRepository eventRepository,
      SuppressionService suppressionService,
      ReputationService reputationService,
      EmailOutboxService outboxService,
      LifecycleEventEncoder eventEncoder,
      com.messaging.emailprovider.delivery.EmailMessageStateService messageStateService,
      @Value("${app.kafka.topics.lifecycle:email.lifecycle.events}") String lifecycleTopic,
      com.messaging.emailprovider.product.webhook.WebhookService webhooks) {
    this.recipientRepository = recipientRepository;
    this.messageRepository = messageRepository;
    this.eventRepository = eventRepository;
    this.suppressionService = suppressionService;
    this.reputationService = reputationService;
    this.outboxService = outboxService;
    this.eventEncoder = eventEncoder;
    this.messageStateService = messageStateService;
    this.lifecycleTopic = lifecycleTopic;
    this.webhooks = webhooks;
  }

  @Transactional
  public FeedbackResultResponse apply(PostfixFeedbackRequest request, String rawPayload) {
    EmailEventSource source = request.resolvedSource();
    String externalId = blankToNull(request.externalEventId());
    if (externalId != null && eventRepository.existsBySourceAndExternalEventId(source, externalId)) {
      EmailRecipient duplicateRecipient = resolveRecipient(request);
      return new FeedbackResultResponse(
          true,
          duplicateRecipient.getMessageId().toString(),
          duplicateRecipient.getId().toString(),
          request.eventType(),
          duplicateRecipient.getStatus());
    }

    EmailRecipient recipient = resolveRecipient(request);
    EmailMessage message =
        messageRepository
            .findById(recipient.getMessageId())
            .orElseThrow(() -> new NotFoundException("Email message not found"));

    Instant occurredAt = request.resolvedOccurredAt();
    boolean stateChanged = transition(message, recipient, request);

    EmailEvent event = new EmailEvent();
    event.setCompanyId(message.getCompanyId());
    event.setMessageId(message.getId());
    event.setRecipientId(recipient.getId());
    event.setEventType(request.eventType());
    event.setSource(source);
    event.setExternalEventId(externalId);
    event.setSmtpStatusCode(truncate(request.smtpStatusCode(), 32));
    event.setDiagnostic(truncate(request.diagnostic(), 2048));
    event.setRawPayload(truncate(rawPayload, 16384));
    event.setOccurredAt(occurredAt);
    event = eventRepository.save(event);

    if (stateChanged) {
      webhooks.enqueue(event, message, recipient);
      reputationService.recordEvent(
          message.getCompanyId(), message.getSendingDomainId(), request.eventType(), occurredAt);
      outboxService.enqueue(
          lifecycleTopic,
          message.getId().toString(),
          eventEncoder.encode(
              message,
              recipient,
              request.eventType(),
              occurredAt,
              request.smtpStatusCode(),
              request.diagnostic()));
      messageStateService.refresh(message.getId());
    }

    return new FeedbackResultResponse(
        false,
        message.getId().toString(),
        recipient.getId().toString(),
        request.eventType(),
        recipient.getStatus());
  }

  private boolean transition(
      EmailMessage message, EmailRecipient recipient, PostfixFeedbackRequest request) {
    EmailRecipientStatus previous = recipient.getStatus();

    if (request.eventType() == EmailEventType.DELIVERED) {
      if (isTerminalFailure(previous)) {
        return false;
      }
      recipient.setStatus(EmailRecipientStatus.DELIVERED);
      recipient.setDeliveredAt(request.resolvedOccurredAt());
      recipient.setNextAttemptAt(null);
    } else if (request.eventType() == EmailEventType.DEFERRED) {
      if (isFinal(previous)) {
        return false;
      }
      recipient.setStatus(EmailRecipientStatus.REMOTE_DEFERRED);
      recipient.setNextAttemptAt(null);
    } else if (request.eventType() == EmailEventType.BOUNCED) {
      if (previous == EmailRecipientStatus.DELIVERED || previous == EmailRecipientStatus.COMPLAINED) {
        return false;
      }
      recipient.setStatus(EmailRecipientStatus.BOUNCED);
      recipient.setDeliveredAt(null);
      recipient.setNextAttemptAt(null);
      if (isPermanentFailure(request.smtpStatusCode())) {
        suppressionService.suppressIfAbsent(
            message.getCompanyId(), recipient.getRecipientAddress(), SuppressionReason.BOUNCE);
      }
    } else if (request.eventType() == EmailEventType.COMPLAINED) {
      recipient.setStatus(EmailRecipientStatus.COMPLAINED);
      recipient.setNextAttemptAt(null);
      suppressionService.suppressIfAbsent(
          message.getCompanyId(), recipient.getRecipientAddress(), SuppressionReason.COMPLAINT);
    } else {
      throw new BadRequestException("Unsupported feedback event");
    }

    recipient.setSmtpStatusCode(truncate(request.smtpStatusCode(), 16));
    recipient.setSmtpResponse(truncate(request.diagnostic(), 2048));
    recipientRepository.save(recipient);
    return previous != recipient.getStatus();
  }

  private EmailRecipient resolveRecipient(PostfixFeedbackRequest request) {
    if (request.recipientId() != null) {
      return recipientRepository
          .findById(request.recipientId())
          .orElseThrow(() -> new NotFoundException("Email recipient not found"));
    }
    if (request.queueId() != null && !request.queueId().isBlank()) {
      return recipientRepository
          .findFirstByPostfixQueueId(request.queueId().trim().toUpperCase())
          .orElseThrow(() -> new NotFoundException("Postfix queue id not found"));
    }
    throw new BadRequestException("recipientId or queueId is required");
  }

  private boolean isPermanentFailure(String status) {
    return status != null && status.trim().startsWith("5");
  }

  private boolean isFinal(EmailRecipientStatus status) {
    return status == EmailRecipientStatus.DELIVERED
        || status == EmailRecipientStatus.BOUNCED
        || status == EmailRecipientStatus.COMPLAINED
        || status == EmailRecipientStatus.SUPPRESSED
        || status == EmailRecipientStatus.FAILED;
  }

  private boolean isTerminalFailure(EmailRecipientStatus status) {
    return status == EmailRecipientStatus.BOUNCED
        || status == EmailRecipientStatus.COMPLAINED
        || status == EmailRecipientStatus.SUPPRESSED;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
