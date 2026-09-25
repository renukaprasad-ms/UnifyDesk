package com.messaging.channel.service;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.service.DeletionPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-independent lifecycle operations for channel connections.
 *
 * <p>This service intentionally does not depend on provider strategies. Provider connection
 * services may use it safely without creating a dependency cycle through ChannelProviderRegistry.
 */
@Service
@RequiredArgsConstructor
public class ChannelConnectionLifecycleService {

  private final ChannelConnectionRepository connectionRepository;
  private final DeletionPurgeService deletionPurgeService;

  @Transactional
  public void cancelPendingPurge(Long companyId, Long connectionId) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Channel connection not found"));

    connection.setDisconnectedAt(null);
    connection.setPurgeAfter(null);
    connection.setDeletionRequestedByUserId(null);
    connectionRepository.save(connection);

    deletionPurgeService.cancel(
        LifecycleEntityType.CHANNEL_CONNECTION, connectionId);
  }
}
