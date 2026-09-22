package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastSenderRepository extends JpaRepository<BroadcastSender, Long> {

  List<BroadcastSender> findByCompanyIdAndStatusOrderByUpdatedAtDesc(
      Long companyId, BroadcastSenderStatus status);

  Optional<BroadcastSender> findByIdAndCompanyId(Long id, Long companyId);

  List<BroadcastSender> findByConnectionId(Long connectionId);

  Optional<BroadcastSender> findByCompanyIdAndProviderAndFromAddressIgnoreCase(
      Long companyId,
      com.messaging.broadcast.enums.BroadcastSenderProvider provider,
      String fromAddress);
}
