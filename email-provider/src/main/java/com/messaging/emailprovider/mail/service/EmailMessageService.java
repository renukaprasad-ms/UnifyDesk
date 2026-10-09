package com.messaging.emailprovider.mail.service;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import com.messaging.emailprovider.domain.enums.SendingDomainStatus;
import com.messaging.emailprovider.domain.repository.SendingDomainRepository;
import com.messaging.emailprovider.mail.dto.EmailAcceptedResponse;
import com.messaging.emailprovider.mail.dto.EmailMessageResponse;
import com.messaging.emailprovider.mail.dto.EmailRecipientResponse;
import com.messaging.emailprovider.mail.dto.SendEmailRequest;
import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.enums.EmailRecipientType;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import com.messaging.emailprovider.queue.EmailOutboxService;
import com.messaging.emailprovider.reputation.ReputationService;
import com.messaging.emailprovider.suppression.SuppressionService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailMessageService {

  private final EmailMessageRepository messageRepository;
  private final EmailRecipientRepository recipientRepository;
  private final SendingDomainRepository domainRepository;
  private final SuppressionService suppressionService;
  private final EmailRequestHasher requestHasher;
  private final EmailOutboxService outboxService;
  private final ReputationService reputationService;
  private final String deliveryTopic;
  private final int maxRecipients;
  private final int maxBodyBytes;

  public EmailMessageService(
      EmailMessageRepository messageRepository,
      EmailRecipientRepository recipientRepository,
      SendingDomainRepository domainRepository,
      SuppressionService suppressionService,
      EmailRequestHasher requestHasher,
      EmailOutboxService outboxService,
      ReputationService reputationService,
      @Value("${app.kafka.topics.delivery-requested:email.delivery.requested}") String deliveryTopic,
      @Value("${app.delivery.max-recipients-per-message:100}") int maxRecipients,
      @Value("${app.delivery.max-body-bytes:5242880}") int maxBodyBytes) {
    this.messageRepository = messageRepository;
    this.recipientRepository = recipientRepository;
    this.domainRepository = domainRepository;
    this.suppressionService = suppressionService;
    this.requestHasher = requestHasher;
    this.outboxService = outboxService;
    this.reputationService = reputationService;
    this.deliveryTopic = deliveryTopic;
    this.maxRecipients = maxRecipients;
    this.maxBodyBytes = maxBodyBytes;
  }

  @Transactional
  public EmailAcceptedResponse accept(
      Long companyId, String idempotencyKey, SendEmailRequest request) {
    validateHeaderValue(request.subject(), "Subject");
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    String requestHash = requestHasher.hash(request);

    if (normalizedKey != null) {
      var existing = messageRepository.findByCompanyIdAndIdempotencyKey(companyId, normalizedKey);
      if (existing.isPresent()) {
        EmailMessage message = existing.get();
        if (!requestHash.equals(message.getRequestHash())) {
          throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        List<EmailRecipient> recipients =
            recipientRepository.findAllByMessageIdOrderByCreatedAtAsc(message.getId());
        return EmailAcceptedResponse.of(
            message,
            (int)
                recipients.stream()
                    .filter(r -> r.getStatus() != EmailRecipientStatus.SUPPRESSED)
                    .count(),
            (int)
                recipients.stream()
                    .filter(r -> r.getStatus() == EmailRecipientStatus.SUPPRESSED)
                    .count(),
            true);
      }
    }

    SendingDomain domain =
        domainRepository
            .findByIdAndCompanyId(request.sendingDomainId(), companyId)
            .orElseThrow(() -> new NotFoundException("Sending domain not found"));
    if (domain.getStatus() != SendingDomainStatus.VERIFIED) {
      throw new BadRequestException("Sending domain must be verified before sending email");
    }

    String from = normalizeEmail(request.from());
    if (!domainPart(from).equals(domain.getDomainName())) {
      throw new BadRequestException("From address must use the verified sending domain");
    }

    validateBody(request);
    if (request.marketing()) {
      if (request.consentReference() == null || request.consentReference().isBlank()) {
        throw new BadRequestException("Marketing email requires a consent reference");
      }
    }
    Map<String, EmailRecipientType> recipients = recipients(request);
    if (recipients.isEmpty()) {
      throw new BadRequestException("At least one recipient is required");
    }
    if (recipients.size() > maxRecipients) {
      throw new BadRequestException("Recipient count exceeds the configured limit");
    }

    EmailMessage message = new EmailMessage();
    message.setCompanyId(companyId);
    message.setSendingDomainId(domain.getId());
    message.setFromAddress(from);
    message.setReplyToAddress(
        request.replyTo() == null || request.replyTo().isBlank()
            ? null
            : normalizeEmail(request.replyTo()));
    message.setSubject(request.subject().trim());
    message.setMarketing(request.marketing());
    message.setConsentReference(request.marketing() ? request.consentReference().trim() : null);
    message.setTextBody(blankToNull(request.text()));
    message.setHtmlBody(blankToNull(request.html()));
    message.setIdempotencyKey(normalizedKey);
    message.setRequestHash(requestHash);
    message.setAcceptedAt(Instant.now());
    message.setStatus(EmailMessageStatus.ACCEPTED);
    message = messageRepository.save(message);

    List<EmailRecipient> rows = new ArrayList<>();
    int suppressed = 0;
    for (Map.Entry<String, EmailRecipientType> entry : recipients.entrySet()) {
      EmailRecipient row = new EmailRecipient();
      row.setMessageId(message.getId());
      row.setRecipientAddress(entry.getKey());
      row.setRecipientType(entry.getValue());
      if (suppressionService.isSuppressed(companyId, entry.getKey())) {
        row.setStatus(EmailRecipientStatus.SUPPRESSED);
        suppressed++;
      } else {
        row.setStatus(EmailRecipientStatus.QUEUED);
      }
      rows.add(row);
    }
    recipientRepository.saveAll(rows);

    int queued = rows.size() - suppressed;
    if (queued == 0) {
      message.setStatus(EmailMessageStatus.SUPPRESSED);
    } else {
      message.setStatus(EmailMessageStatus.QUEUED);
      reputationService.recordAccepted(
          companyId, domain.getId(), queued, message.getAcceptedAt());
      outboxService.enqueue(deliveryTopic, message.getId().toString(), message.getId().toString());
    }
    message = messageRepository.save(message);
    return EmailAcceptedResponse.of(message, queued, suppressed, false);
  }

  @Transactional(readOnly = true)
  public EmailMessageResponse get(Long companyId, Long messageId) {
    EmailMessage message =
        messageRepository
            .findByIdAndCompanyId(messageId, companyId)
            .orElseThrow(() -> new NotFoundException("Email message not found"));
    return EmailMessageResponse.from(
        message,
        recipientRepository.findAllByMessageIdOrderByCreatedAtAsc(messageId).stream()
            .map(EmailRecipientResponse::from)
            .toList());
  }

  @Transactional(readOnly = true)
  public List<EmailRecipientResponse> recipients(Long companyId, Long messageId) {
    EmailMessage message =
        messageRepository
            .findByIdAndCompanyId(messageId, companyId)
            .orElseThrow(() -> new NotFoundException("Email message not found"));
    return recipientRepository.findAllByMessageIdOrderByCreatedAtAsc(message.getId()).stream()
        .map(EmailRecipientResponse::from)
        .toList();
  }

  private Map<String, EmailRecipientType> recipients(SendEmailRequest request) {
    Map<String, EmailRecipientType> result = new LinkedHashMap<>();
    add(result, request.bcc(), EmailRecipientType.BCC);
    add(result, request.cc(), EmailRecipientType.CC);
    add(result, request.to(), EmailRecipientType.TO);
    return result;
  }

  private void add(
      Map<String, EmailRecipientType> result, List<String> values, EmailRecipientType type) {
    if (values == null) {
      return;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        result.put(normalizeEmail(value), type);
      }
    }
  }

  private String normalizeEmail(String email) {
    String value = email == null ? "" : email.trim();
    if (value.isBlank()
        || value.indexOf('\r') >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('<') >= 0
        || value.indexOf('>') >= 0
        || value.chars().anyMatch(character -> character > 127)
        || value.chars().anyMatch(Character::isWhitespace)
        || value.indexOf('@') != value.lastIndexOf('@')) {
      throw new BadRequestException("Invalid email address");
    }
    return value.toLowerCase(Locale.ROOT);
  }

  private String domainPart(String email) {
    int at = email.lastIndexOf('@');
    if (at <= 0 || at == email.length() - 1) {
      throw new BadRequestException("Invalid email address");
    }
    return email.substring(at + 1);
  }

  private void validateHeaderValue(String value, String field) {
    if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
      throw new BadRequestException(field + " must not contain line breaks");
    }
  }

  private void validateBody(SendEmailRequest request) {
    if ((request.text() == null || request.text().isBlank())
        && (request.html() == null || request.html().isBlank())) {
      throw new BadRequestException("Either text or html body is required");
    }
    int bytes =
        (request.text() == null ? 0 : request.text().getBytes(StandardCharsets.UTF_8).length)
            + (request.html() == null
                ? 0
                : request.html().getBytes(StandardCharsets.UTF_8).length);
    if (bytes > maxBodyBytes) {
      throw new BadRequestException("Email body exceeds the configured size limit");
    }
  }

  private String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String key = value.trim();
    if (key.length() > 128) {
      throw new BadRequestException("Idempotency-Key must not exceed 128 characters");
    }
    return key;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
