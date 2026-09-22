package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.*;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.id.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the generic channel connection and provider-specific sender model in one transaction
 * after external provider validation has succeeded.
 */
@Service
@RequiredArgsConstructor
public class BroadcastSenderPersistenceService {

  private final BroadcastSenderConnectionRepository connectionRepository;
  private final BroadcastSenderRepository senderRepository;
  private final ChannelConnectionRepository channelConnectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;

  @Transactional
  public BroadcastSenderResponse create(
      BroadcastSenderConnection connection, BroadcastSender sender) {
    Long connectionId = connection.getId() == null ? IdGenerator.nextId() : connection.getId();
    connection.setId(connectionId);
    connection.setChannelConnectionId(connectionId);

    ChannelConnection channelConnection = new ChannelConnection();
    channelConnection.setId(connectionId);
    channelConnection.setCompanyId(connection.getCompanyId());
    channelConnection.setChannelType(connection.getProvider().channelType());
    channelConnection.setProviderType(channelProvider(connection.getProvider()));
    channelConnection.setName(connection.getName());
    channelConnection.setExternalAccountId(sender.getFromAddress());
    channelConnection.setStatus(ChannelConnectionStatus.ACTIVE);
    channelConnection.setCreatedByUserId(connection.getCreatedByUserId());
    channelConnection.setLastSyncAt(connection.getLastValidatedAt());
    channelConnectionRepository.save(channelConnection);

    BroadcastSenderConnection savedConnection = connectionRepository.save(connection);

    Long senderId = sender.getId() == null ? IdGenerator.nextId() : sender.getId();
    sender.setId(senderId);
    sender.setConnectionId(savedConnection.getId());
    BroadcastSender savedSender = senderRepository.save(sender);

    SenderIdentity identity = new SenderIdentity();
    identity.setId(senderId);
    identity.setConnection(channelConnection);
    identity.setIdentityType(
        connection.getProvider().channelType() == ChannelType.SMS
            ? SenderIdentityType.SMS_HEADER
            : SenderIdentityType.EMAIL_ADDRESS);
    identity.setValue(savedSender.getFromAddress());
    identity.setExternalId(senderId.toString());
    identity.setStatus(SenderIdentityStatus.ACTIVE);
    identity.setDefaultIdentity(true);
    senderIdentityRepository.save(identity);

    return BroadcastSenderResponse.from(savedSender, savedConnection);
  }

  private ProviderType channelProvider(BroadcastSenderProvider provider) {
    return switch (provider) {
      case AMAZON_SES -> ProviderType.AMAZON_SES;
      case AZURE_EMAIL -> ProviderType.AZURE_EMAIL;
      case SENDGRID -> ProviderType.SENDGRID;
      case MSG91 -> ProviderType.MSG91;
      case GMAIL, MICROSOFT, IMAP_SMTP, SMTP ->
          throw new IllegalArgumentException(
              "Mailbox provider cannot be used for new Broadcast senders: " + provider);
    };
  }
}
