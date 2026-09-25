package com.messaging.emailprovider.feedback;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.feedback.dto.EmailEventResponse;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailEventQueryService {

  private final EmailMessageRepository messageRepository;
  private final EmailEventRepository eventRepository;

  public EmailEventQueryService(
      EmailMessageRepository messageRepository, EmailEventRepository eventRepository) {
    this.messageRepository = messageRepository;
    this.eventRepository = eventRepository;
  }

  @Transactional(readOnly = true)
  public List<EmailEventResponse> list(Long companyId, Long messageId) {
    messageRepository
        .findByIdAndCompanyId(messageId, companyId)
        .orElseThrow(() -> new NotFoundException("Email message not found"));
    return eventRepository.findAllByMessageIdOrderByOccurredAtAsc(messageId).stream()
        .map(EmailEventResponse::from)
        .toList();
  }
}
