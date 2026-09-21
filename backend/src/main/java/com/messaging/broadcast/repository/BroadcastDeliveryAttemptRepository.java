package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastDeliveryAttemptRepository
    extends JpaRepository<BroadcastDeliveryAttempt, Long> {}
