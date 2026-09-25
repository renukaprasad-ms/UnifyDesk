package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastSetupSession;
import com.messaging.broadcast.enums.BroadcastSetupStatus;
import com.messaging.channel.enums.ChannelType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastSetupSessionRepository
    extends JpaRepository<BroadcastSetupSession, Long> {

  Optional<BroadcastSetupSession>
      findFirstByCompanyIdAndChannelTypeAndStatusInOrderByUpdatedAtDesc(
          Long companyId,
          ChannelType channelType,
          Collection<BroadcastSetupStatus> statuses);

  Optional<BroadcastSetupSession> findByIdAndCompanyId(Long id, Long companyId);
}
