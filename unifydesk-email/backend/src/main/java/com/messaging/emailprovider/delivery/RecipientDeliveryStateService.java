package com.messaging.emailprovider.delivery;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import com.messaging.emailprovider.smtp.PostfixQueueIdExtractor;
import com.messaging.emailprovider.smtp.SmtpDeliveryResult;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipientDeliveryStateService {

  private final EmailRecipientRepository recipientRepository;
  private final EmailDeliveryAttemptRepository attemptRepository;
  private final PostfixQueueIdExtractor queueIdExtractor;
  private final com.messaging.emailprovider.product.usage.ProductUsageService usage;

  public RecipientDeliveryStateService(
      EmailRecipientRepository recipientRepository,
      EmailDeliveryAttemptRepository attemptRepository,
      PostfixQueueIdExtractor queueIdExtractor,
      com.messaging.emailprovider.product.usage.ProductUsageService usage) {
    this.recipientRepository = recipientRepository;
    this.attemptRepository = attemptRepository;
    this.queueIdExtractor = queueIdExtractor;
    this.usage = usage;
  }

  @Transactional
  public EmailRecipient claim(Long recipientId) {
    Instant now = Instant.now();
    if (recipientRepository.claimForDelivery(recipientId, now) != 1) {
      return null;
    }
    return recipientRepository
        .findById(recipientId)
        .orElseThrow(() -> new NotFoundException("Email recipient not found"));
  }

  @Transactional
  public void handedOff(EmailRecipient recipient, SmtpDeliveryResult result, Instant startedAt) {
    EmailRecipient current = require(recipient.getId());
    current.setStatus(EmailRecipientStatus.HANDED_OFF);
    current.setSmtpStatusCode(result.statusCode());
    current.setSmtpResponse(truncate(result.response()));
    current.setPostfixQueueId(queueIdExtractor.extract(result.response()).orElse(null));
    current.setHandedOffAt(Instant.now());
    current.setNextAttemptAt(null);
    recipientRepository.save(current);
    audit(current, DeliveryAttemptOutcome.HANDED_OFF, result, startedAt);
    usage.recordHandoff(current);
  }

  @Transactional
  public void unknown(EmailRecipient recipient, SmtpDeliveryResult result, Instant startedAt) {
    EmailRecipient current = require(recipient.getId());
    current.setStatus(EmailRecipientStatus.UNKNOWN);
    current.setSmtpStatusCode(result.statusCode());
    current.setSmtpResponse(truncate(result.response()));
    current.setNextAttemptAt(null);
    recipientRepository.save(current);
    audit(current, DeliveryAttemptOutcome.UNKNOWN, result, startedAt);
  }

  @Transactional
  public void deferred(
      EmailRecipient recipient,
      SmtpDeliveryResult result,
      Instant startedAt,
      int maxAttempts,
      long initialRetrySeconds,
      long maxRetrySeconds) {
    EmailRecipient current = require(recipient.getId());
    current.setSmtpStatusCode(result.statusCode());
    current.setSmtpResponse(truncate(result.response()));

    if (current.getAttemptCount() >= maxAttempts) {
      current.setStatus(EmailRecipientStatus.FAILED);
      current.setNextAttemptAt(null);
      recipientRepository.save(current);
      audit(current, DeliveryAttemptOutcome.FAILED, result, startedAt);
      return;
    }

    long multiplier = 1L << Math.min(Math.max(current.getAttemptCount() - 1, 0), 20);
    long delay = Math.min(maxRetrySeconds, initialRetrySeconds * multiplier);
    current.setStatus(EmailRecipientStatus.DEFERRED);
    current.setNextAttemptAt(Instant.now().plusSeconds(delay));
    recipientRepository.save(current);
    audit(current, DeliveryAttemptOutcome.DEFERRED, result, startedAt);
  }

  @Transactional
  public void failed(EmailRecipient recipient, SmtpDeliveryResult result, Instant startedAt) {
    EmailRecipient current = require(recipient.getId());
    current.setStatus(EmailRecipientStatus.FAILED);
    current.setSmtpStatusCode(result.statusCode());
    current.setSmtpResponse(truncate(result.response()));
    current.setNextAttemptAt(null);
    recipientRepository.save(current);
    audit(current, DeliveryAttemptOutcome.FAILED, result, startedAt);
  }

  @Transactional
  public void suppressed(EmailRecipient recipient, Instant startedAt) {
    EmailRecipient current = require(recipient.getId());
    current.setStatus(EmailRecipientStatus.SUPPRESSED);
    current.setNextAttemptAt(null);
    current.setSmtpResponse("Recipient is suppressed");
    recipientRepository.save(current);

    SmtpDeliveryResult result =
        new SmtpDeliveryResult(
            com.messaging.emailprovider.smtp.SmtpDeliveryOutcome.FAILED,
            null,
            "Recipient is suppressed");
    audit(current, DeliveryAttemptOutcome.SUPPRESSED, result, startedAt);
  }

  @Transactional
  public int recoverStale(int staleAfterSeconds) {
    Instant now = Instant.now();
    return recipientRepository.recoverStale(now.minusSeconds(staleAfterSeconds), now);
  }

  private EmailRecipient require(Long id) {
    return recipientRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Email recipient not found"));
  }

  private void audit(
      EmailRecipient recipient,
      DeliveryAttemptOutcome outcome,
      SmtpDeliveryResult result,
      Instant startedAt) {
    EmailDeliveryAttempt attempt = new EmailDeliveryAttempt();
    attempt.setRecipientId(recipient.getId());
    attempt.setAttemptNumber(recipient.getAttemptCount());
    attempt.setOutcome(outcome);
    attempt.setSmtpStatusCode(result.statusCode());
    attempt.setSmtpResponse(truncate(result.response()));
    attempt.setStartedAt(startedAt);
    attempt.setCompletedAt(Instant.now());
    attemptRepository.save(attempt);
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 2048) {
      return value;
    }
    return value.substring(0, 2048);
  }
}
