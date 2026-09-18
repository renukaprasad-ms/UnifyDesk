package com.messaging.inbox.repository;

import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.ChannelSyncState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelSyncStateRepository extends JpaRepository<ChannelSyncState, Long> {

  Optional<ChannelSyncState> findByConnectionIdAndProviderType(
      Long connectionId, ProviderType providerType);
}
