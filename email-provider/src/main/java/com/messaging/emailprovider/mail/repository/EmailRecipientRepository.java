package com.messaging.emailprovider.mail.repository;

import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRecipientRepository extends JpaRepository<EmailRecipient, Long> {

  List<EmailRecipient> findAllByMessageIdOrderByCreatedAtAsc(Long messageId);

  List<EmailRecipient> findAllByStatusOrderByCreatedAtAsc(EmailRecipientStatus status);
}
