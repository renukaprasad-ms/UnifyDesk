package com.messaging.emailprovider.feedback;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {

  boolean existsBySourceAndExternalEventId(EmailEventSource source, String externalEventId);

  List<EmailEvent> findAllByMessageIdOrderByOccurredAtAsc(Long messageId);
}
