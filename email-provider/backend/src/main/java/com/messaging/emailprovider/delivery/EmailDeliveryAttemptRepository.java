package com.messaging.emailprovider.delivery;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {

  List<EmailDeliveryAttempt> findAllByRecipientIdOrderByAttemptNumberAsc(Long recipientId);
}
