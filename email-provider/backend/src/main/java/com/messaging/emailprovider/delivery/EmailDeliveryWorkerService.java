package com.messaging.emailprovider.delivery;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.dkim.service.DkimKeyService;
import com.messaging.emailprovider.dkim.service.DkimSigner;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import com.messaging.emailprovider.domain.enums.SendingDomainStatus;
import com.messaging.emailprovider.domain.repository.SendingDomainRepository;
import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import com.messaging.emailprovider.mime.MimeMessageBuilder;
import com.messaging.emailprovider.smtp.PostfixSmtpClient;
import com.messaging.emailprovider.smtp.SmtpDeliveryOutcome;
import com.messaging.emailprovider.smtp.SmtpDeliveryResult;
import com.messaging.emailprovider.suppression.SuppressionService;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryWorkerService {

  private final EmailMessageRepository messageRepository;
  private final EmailRecipientRepository recipientRepository;
  private final SendingDomainRepository domainRepository;
  private final SuppressionService suppressionService;
  private final DkimKeyService dkimKeyService;
  private final DkimSigner dkimSigner;
  private final MimeMessageBuilder mimeBuilder;
  private final PostfixSmtpClient smtpClient;
  private final RecipientDeliveryStateService recipientState;
  private final EmailMessageStateService messageState;
  private final ProductDeliveryGate productDeliveryGate;
  private final EmailSendingRateLimiter sendingRateLimiter;
  private final int maxAttempts;
  private final long initialRetrySeconds;
  private final long maxRetrySeconds;
  private final String envelopeFromDomain;

  public EmailDeliveryWorkerService(
      EmailMessageRepository messageRepository,
      EmailRecipientRepository recipientRepository,
      SendingDomainRepository domainRepository,
      SuppressionService suppressionService,
      DkimKeyService dkimKeyService,
      DkimSigner dkimSigner,
      MimeMessageBuilder mimeBuilder,
      PostfixSmtpClient smtpClient,
      RecipientDeliveryStateService recipientState,
      EmailMessageStateService messageState,
      ProductDeliveryGate productDeliveryGate,
      EmailSendingRateLimiter sendingRateLimiter,
      @Value("${app.delivery.retry.max-attempts:8}") int maxAttempts,
      @Value("${app.delivery.retry.initial-seconds:60}") long initialRetrySeconds,
      @Value("${app.delivery.retry.max-seconds:3600}") long maxRetrySeconds,
      @Value("${app.smtp.envelope-from-domain:}") String envelopeFromDomain) {
    this.messageRepository = messageRepository;
    this.recipientRepository = recipientRepository;
    this.domainRepository = domainRepository;
    this.suppressionService = suppressionService;
    this.dkimKeyService = dkimKeyService;
    this.dkimSigner = dkimSigner;
    this.mimeBuilder = mimeBuilder;
    this.smtpClient = smtpClient;
    this.recipientState = recipientState;
    this.messageState = messageState;
    this.productDeliveryGate = productDeliveryGate;
    this.sendingRateLimiter = sendingRateLimiter;
    this.maxAttempts = maxAttempts;
    this.initialRetrySeconds = initialRetrySeconds;
    this.maxRetrySeconds = maxRetrySeconds;
    this.envelopeFromDomain = envelopeFromDomain;
  }

  public void deliverMessage(Long messageId) {
    EmailMessage message = messageRepository.findById(messageId).orElse(null);
    if (message == null) {
      return;
    }

    // Independent product projects are not allowed to dispatch while disabled/suspended.
    if (!productDeliveryGate.permitted(message.getCompanyId())) {
      recipientRepository.deferForProductPause(messageId, Instant.now().plusSeconds(3600), Instant.now());
      messageState.refresh(messageId);
      return;
    }

    List<EmailRecipient> allRecipients =
        recipientRepository.findAllByMessageIdOrderByCreatedAtAsc(messageId);
    List<Long> dueIds = recipientRepository.findDueRecipientIds(messageId, Instant.now());
    if (dueIds.isEmpty()) {
      messageState.refresh(messageId);
      return;
    }

    SendingDomain domain = domainRepository.findById(message.getSendingDomainId()).orElse(null);
    if (domain == null || domain.getStatus() != SendingDomainStatus.VERIFIED) {
      deferInfrastructure(
          dueIds,
          new SmtpDeliveryResult(
              SmtpDeliveryOutcome.DEFERRED, null, "Sending domain is not currently verified"));
      messageState.refresh(messageId);
      return;
    }

    final DkimKey dkimKey;
    final java.security.PrivateKey privateKey;
    try {
      dkimKey = dkimKeyService.getActiveForDomain(domain.getId());
      privateKey = dkimKeyService.loadPrivateKey(domain.getId());
    } catch (RuntimeException exception) {
      deferInfrastructure(
          dueIds,
          new SmtpDeliveryResult(
              SmtpDeliveryOutcome.DEFERRED, null, "DKIM signing key is temporarily unavailable"));
      messageState.refresh(messageId);
      return;
    }

    for (Long recipientId : dueIds) {
      EmailRecipient recipient = recipientState.claim(recipientId);
      if (recipient == null) {
        continue;
      }

      Instant startedAt = Instant.now();
      if (suppressionService.isSuppressed(message.getCompanyId(), recipient.getRecipientAddress())) {
        recipientState.suppressed(recipient, startedAt);
        continue;
      }

      // Shared atomic limits; a Redis outage must not allow unchecked delivery.
      if (!sendingRateLimiter.permit(message.getCompanyId(), domain.getId())) {
        recipientState.deferred(
            recipient,
            new SmtpDeliveryResult(SmtpDeliveryOutcome.DEFERRED, null, "Sending rate limit reached or unavailable"),
            startedAt,
            maxAttempts,
            initialRetrySeconds,
            maxRetrySeconds);
        continue;
      }

      final byte[] signed;
      try {
        byte[] raw = mimeBuilder.build(message, recipient, allRecipients, domain.getDomainName());
        signed =
            dkimSigner.sign(raw, domain.getDomainName(), dkimKey.getSelector(), privateKey);
      } catch (RuntimeException exception) {
        recipientState.deferred(
            recipient,
            new SmtpDeliveryResult(
                SmtpDeliveryOutcome.DEFERRED, null, "Unable to build or sign MIME message"),
            startedAt,
            maxAttempts,
            initialRetrySeconds,
            maxRetrySeconds);
        continue;
      }

      SmtpDeliveryResult result;
      try {
        result =
            smtpClient.handoff(
                envelopeFrom(message, recipient), recipient.getRecipientAddress(), signed);
      } catch (RuntimeException exception) {
        result =
            new SmtpDeliveryResult(
                SmtpDeliveryOutcome.DEFERRED, null, "SMTP handoff failed unexpectedly");
      }

      if (result.outcome() == SmtpDeliveryOutcome.HANDED_OFF) {
        recipientState.handedOff(recipient, result, startedAt);
      } else if (result.outcome() == SmtpDeliveryOutcome.UNKNOWN) {
        recipientState.unknown(recipient, result, startedAt);
      } else if (result.outcome() == SmtpDeliveryOutcome.DEFERRED) {
        recipientState.deferred(
            recipient,
            result,
            startedAt,
            maxAttempts,
            initialRetrySeconds,
            maxRetrySeconds);
      } else {
        recipientState.failed(recipient, result, startedAt);
      }
    }

    messageState.refresh(messageId);
  }

  private void deferInfrastructure(List<Long> recipientIds, SmtpDeliveryResult result) {
    for (Long recipientId : recipientIds) {
      EmailRecipient recipient = recipientState.claim(recipientId);
      if (recipient != null) {
        recipientState.deferred(
            recipient,
            result,
            Instant.now(),
            maxAttempts,
            initialRetrySeconds,
            maxRetrySeconds);
      }
    }
  }

  private String envelopeFrom(EmailMessage message, EmailRecipient recipient) {
    if (envelopeFromDomain == null || envelopeFromDomain.isBlank()) {
      return message.getFromAddress();
    }
    return "bounce+"
        + message.getId()
        + "."
        + recipient.getId()
        + "@"
        + envelopeFromDomain.trim().toLowerCase();
  }
}
