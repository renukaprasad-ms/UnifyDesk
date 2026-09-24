package com.messaging.emailprovider.mail.repository;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

  Optional<EmailMessage> findByIdAndCompanyId(Long id, Long companyId);

  List<EmailMessage> findAllByCompanyIdAndStatusOrderByCreatedAtAsc(
      Long companyId, EmailMessageStatus status);
}
