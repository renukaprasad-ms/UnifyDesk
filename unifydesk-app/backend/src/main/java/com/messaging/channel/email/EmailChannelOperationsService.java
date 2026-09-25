package com.messaging.channel.email;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.DisconnectDataPolicy;
import com.messaging.channel.provider.ChannelProviderRegistry;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailChannelOperationsService {

  private final ChannelConnectionRepository connectionRepository;
  private final ChannelProviderRegistry providerRegistry;
  private final ChannelConnectionService channelConnectionService;
  private final EmailProviderMetrics metrics;

  public EmailSendResult send(
      Long companyId, Long connectionId, EmailSendCommand command) {
    ChannelConnection connection = requireActiveEmail(companyId, connectionId);
    EmailSendProvider provider =
        providerRegistry.requireCapability(connection.getProviderType(), EmailSendProvider.class);
    return metrics.record(
        connection.getProviderType(),
        EmailProviderOperation.SEND,
        () -> provider.send(connection, command));
  }

  public EmailSyncQueuedResult queueSync(
      Long companyId, Long userId, Long connectionId, EmailSyncRequest request) {
    ChannelConnection connection = requireActiveEmail(companyId, connectionId);
    EmailSyncProvider provider =
        providerRegistry.requireCapability(connection.getProviderType(), EmailSyncProvider.class);
    return metrics.record(
        connection.getProviderType(),
        EmailProviderOperation.SYNC,
        () -> provider.queueSync(connection, userId, request));
  }

  public AsyncJobResponse activeSync(Long companyId, Long connectionId) {
    ChannelConnection connection = requireEmail(companyId, connectionId);
    EmailSyncProvider provider =
        providerRegistry.requireCapability(connection.getProviderType(), EmailSyncProvider.class);
    return metrics.record(
        connection.getProviderType(),
        EmailProviderOperation.ACTIVE_SYNC,
        () -> provider.activeSync(connection));
  }

  public ChannelConnectionResponse disconnect(
      Long companyId,
      Long connectionId,
      Long requestedByUserId,
      DisconnectDataPolicy dataPolicy) {
    ChannelConnection connection = requireEmail(companyId, connectionId);
    return metrics.record(
        connection.getProviderType(),
        EmailProviderOperation.DISCONNECT,
        () ->
            channelConnectionService.disconnect(
                companyId, connectionId, requestedByUserId, dataPolicy));
  }

  private ChannelConnection requireActiveEmail(Long companyId, Long connectionId) {
    ChannelConnection connection = requireEmail(companyId, connectionId);
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Email connection is not active");
    }
    return connection;
  }

  private ChannelConnection requireEmail(Long companyId, Long connectionId) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Email connection not found"));

    if (connection.getChannelType() != ChannelType.EMAIL) {
      throw new BadRequestException("Connection is not an email channel");
    }
    return connection;
  }
}
