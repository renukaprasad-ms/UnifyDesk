package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastSenderConnectionRepository
    extends JpaRepository<BroadcastSenderConnection, Long> {

  Optional<BroadcastSenderConnection> findByIdAndCompanyId(Long id, Long companyId);
}
