package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastGroup;
import com.messaging.channel.enums.ChannelType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastGroupRepository extends JpaRepository<BroadcastGroup, Long> {
  Page<BroadcastGroup> findByCompanyIdAndChannelTypeOrderByUpdatedAtDesc(
      Long companyId, ChannelType channelType, Pageable pageable);

  Optional<BroadcastGroup> findByIdAndCompanyId(Long id, Long companyId);
}
