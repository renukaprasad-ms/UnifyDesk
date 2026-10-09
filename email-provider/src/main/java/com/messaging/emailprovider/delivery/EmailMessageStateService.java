package com.messaging.emailprovider.delivery;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailMessageStateService {

  private final EmailMessageRepository messageRepository;
  private final EmailRecipientRepository recipientRepository;

  public EmailMessageStateService(
      EmailMessageRepository messageRepository, EmailRecipientRepository recipientRepository) {
    this.messageRepository = messageRepository;
    this.recipientRepository = recipientRepository;
  }

  @Transactional
  public void refresh(Long messageId) {
    EmailMessage message = messageRepository.findById(messageId).orElse(null);
    if (message == null || message.getStatus() == EmailMessageStatus.CANCELLED) {
      return;
    }

    List<EmailRecipient> recipients =
        recipientRepository.findAllByMessageIdOrderByCreatedAtAsc(messageId);
    if (recipients.isEmpty()) {
      message.setStatus(EmailMessageStatus.FAILED);
      messageRepository.save(message);
      return;
    }

    long delivered =
        recipients.stream().filter(r -> r.getStatus() == EmailRecipientStatus.DELIVERED).count();
    long handedOff =
        recipients.stream().filter(r -> r.getStatus() == EmailRecipientStatus.HANDED_OFF).count();
    long suppressed =
        recipients.stream().filter(r -> r.getStatus() == EmailRecipientStatus.SUPPRESSED).count();
    boolean processing =
        recipients.stream()
            .anyMatch(
                r ->
                    r.getStatus() == EmailRecipientStatus.PROCESSING
                        || r.getStatus() == EmailRecipientStatus.QUEUED);
    boolean unknown = recipients.stream().anyMatch(r -> r.getStatus() == EmailRecipientStatus.UNKNOWN);
    boolean deferred =
        recipients.stream()
            .anyMatch(
                r ->
                    r.getStatus() == EmailRecipientStatus.DEFERRED
                        || r.getStatus() == EmailRecipientStatus.REMOTE_DEFERRED);

    if (processing) {
      message.setStatus(EmailMessageStatus.PROCESSING);
    } else if (unknown || deferred) {
      message.setStatus(EmailMessageStatus.DEFERRED);
    } else if (delivered == recipients.size()) {
      message.setStatus(EmailMessageStatus.DELIVERED);
    } else if (delivered + handedOff == recipients.size()) {
      message.setStatus(
          delivered == recipients.size()
              ? EmailMessageStatus.DELIVERED
              : EmailMessageStatus.HANDED_OFF);
    } else if (suppressed == recipients.size()) {
      message.setStatus(EmailMessageStatus.SUPPRESSED);
    } else if (delivered + handedOff > 0) {
      message.setStatus(EmailMessageStatus.PARTIALLY_DELIVERED);
    } else {
      message.setStatus(EmailMessageStatus.FAILED);
    }
    messageRepository.save(message);
  }
}
