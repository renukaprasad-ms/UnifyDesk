package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.*;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.id.IdGenerator;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.service.DeletionPurgeService;
import java.util.List;
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
  private final DeletionPurgeService deletionPurgeService;

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
    channelConnection.setExternalAccountId(
        connection.getProvider() == BroadcastSenderProvider.MSG91_SMS
                || connection.getProvider() == BroadcastSenderProvider.MSG91_EMAIL
            ? connection.getExternalId()
            : sender.getFromAddress());
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
    identity.setIdentityType(senderIdentityType(connection.getProvider().channelType()));
    identity.setValue(savedSender.getFromAddress());
    identity.setExternalId(senderId.toString());
    identity.setStatus(SenderIdentityStatus.ACTIVE);
    identity.setDefaultIdentity(true);
    senderIdentityRepository.save(identity);

    return BroadcastSenderResponse.from(savedSender, savedConnection);
  }

  @Transactional
  public BroadcastSenderResponse reactivate(
      BroadcastSenderConnection connection, BroadcastSender sender) {
    Long channelConnectionId =
        connection.getChannelConnectionId() == null
            ? connection.getId()
            : connection.getChannelConnectionId();

    ChannelConnection channelConnection =
        channelConnectionRepository
            .findByIdAndCompanyId(channelConnectionId, connection.getCompanyId())
            .orElseGet(
                () -> {
                  ChannelConnection restored = new ChannelConnection();
                  restored.setId(channelConnectionId);
                  restored.setCompanyId(connection.getCompanyId());
                  restored.setChannelType(connection.getProvider().channelType());
                  restored.setProviderType(channelProvider(connection.getProvider()));
                  restored.setCreatedByUserId(connection.getCreatedByUserId());
                  return restored;
                });

    channelConnection.setName(connection.getName());
    channelConnection.setExternalAccountId(
        connection.getProvider() == BroadcastSenderProvider.MSG91_SMS
                || connection.getProvider() == BroadcastSenderProvider.MSG91_EMAIL
            ? connection.getExternalId()
            : sender.getFromAddress());
    channelConnection.setStatus(ChannelConnectionStatus.ACTIVE);
    channelConnection.setLastSyncAt(connection.getLastValidatedAt());
    channelConnection.setDisconnectedAt(null);
    channelConnection.setPurgeAfter(null);
    channelConnection.setDeletionRequestedByUserId(null);
    ChannelConnection savedChannelConnection =
        channelConnectionRepository.save(channelConnection);

    connection.setChannelConnectionId(savedChannelConnection.getId());
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    BroadcastSenderConnection savedConnection = connectionRepository.save(connection);

    sender.setConnectionId(savedConnection.getId());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    BroadcastSender savedSender = senderRepository.save(sender);

    List<SenderIdentity> identities =
        senderIdentityRepository.findByConnectionIdOrderByCreatedAtAsc(
            savedChannelConnection.getId());
    boolean identityExists =
        identities.stream().anyMatch(existing -> existing.getId().equals(savedSender.getId()));
    if (!identityExists) {
      SenderIdentity restored = new SenderIdentity();
      restored.setId(savedSender.getId());
      restored.setConnection(savedChannelConnection);
      restored.setIdentityType(senderIdentityType(connection.getProvider().channelType()));
      restored.setExternalId(savedSender.getId().toString());
      identities.add(restored);
    }

    for (SenderIdentity existing : identities) {
      boolean selected = existing.getId().equals(savedSender.getId());
      existing.setDefaultIdentity(selected);
      if (selected) {
        existing.setValue(savedSender.getFromAddress());
        existing.setStatus(SenderIdentityStatus.ACTIVE);
      }
    }
    senderIdentityRepository.saveAll(identities);

    deletionPurgeService.cancel(
        LifecycleEntityType.CHANNEL_CONNECTION, savedChannelConnection.getId());
    return BroadcastSenderResponse.from(savedSender, savedConnection);
  }

  private SenderIdentityType senderIdentityType(ChannelType channelType) {
    return switch (channelType) {
      case EMAIL -> SenderIdentityType.EMAIL_ADDRESS;
      case SMS -> SenderIdentityType.SMS_HEADER;
      case WHATSAPP -> SenderIdentityType.PHONE_NUMBER;
    };
  }

  private ProviderType channelProvider(BroadcastSenderProvider provider) {
    return switch (provider) {
      case AMAZON_SES -> ProviderType.AMAZON_SES;
      case UNIFYDESK_EMAIL -> ProviderType.UNIFYDESK_EMAIL;
      case AZURE_EMAIL -> ProviderType.AZURE_EMAIL;
      case SENDGRID -> ProviderType.SENDGRID;
      case MSG91_EMAIL -> ProviderType.MSG91_EMAIL;
      case MSG91_SMS -> ProviderType.MSG91_SMS;
      case META_WHATSAPP -> ProviderType.META_WHATSAPP;
      case GMAIL, MICROSOFT, IMAP_SMTP, SMTP ->
          throw new IllegalArgumentException(
              "Mailbox provider cannot be used for new Broadcast senders: " + provider);
    };
  }
}
