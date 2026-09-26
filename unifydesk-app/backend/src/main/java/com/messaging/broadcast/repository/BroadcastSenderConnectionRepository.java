package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastSenderConnectionRepository
    extends JpaRepository<BroadcastSenderConnection, Long> {

  Optional<BroadcastSenderConnection> findByIdAndCompanyId(Long id, Long companyId);

  Optional<BroadcastSenderConnection> findByChannelConnectionIdAndCompanyId(
      Long channelConnectionId, Long companyId);

  Optional<BroadcastSenderConnection>
      findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
          Long companyId,
          BroadcastSenderProvider provider,
          BroadcastSenderManagementMode managementMode,
          BroadcastSenderConnectionStatus status);
}
