package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastDeliveryAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastDeliveryAttemptRepository
    extends JpaRepository<BroadcastDeliveryAttempt, Long> {

  Optional<BroadcastDeliveryAttempt> findFirstByRecipientIdOrderByAttemptNoDesc(Long recipientId);
}
