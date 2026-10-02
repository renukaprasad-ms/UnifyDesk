package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderAuthMode;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.meta.runtime.MetaWhatsAppControlPlaneProjectionRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.id.IdGenerator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastWhatsAppSenderAdapter {

  private final ChannelConnectionRepository channelConnectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final MetaWhatsAppControlPlaneProjectionRepository projectionRepository;
  private final BroadcastSenderConnectionRepository broadcastConnectionRepository;
  private final BroadcastSenderRepository broadcastSenderRepository;

  @Transactional
  public void synchronizeCompany(long companyId) {
    List<ChannelConnection> connections =
        channelConnectionRepository.findByCompanyIdAndChannelTypeOrderByCreatedAtDesc(
            companyId, ChannelType.WHATSAPP);
    for (ChannelConnection connection : connections) {
      if (connection.getProviderType() == ProviderType.META_WHATSAPP) {
        synchronize(connection);
      }
    }
  }

  @Transactional
  public BroadcastSender reconcile(long companyId, long senderId) {
    BroadcastSender sender =
        broadcastSenderRepository
            .findByIdAndCompanyId(senderId, companyId)
            .orElseThrow(
                () -> new com.messaging.common.exception.NotFoundException(
                    "Broadcast sender not found"));
    if (sender.getProvider() != BroadcastSenderProvider.META_WHATSAPP) return sender;
    BroadcastSenderConnection adapterConnection =
        broadcastConnectionRepository
            .findByIdAndCompanyId(sender.getConnectionId(), companyId)
            .orElseThrow(
                () -> new com.messaging.common.exception.NotFoundException(
                    "Broadcast sender connection not found"));
    if (adapterConnection.getChannelConnectionId() == null) return sender;
    ChannelConnection channelConnection =
        channelConnectionRepository
            .findByIdAndCompanyId(adapterConnection.getChannelConnectionId(), companyId)
            .orElseThrow(
                () -> new com.messaging.common.exception.NotFoundException(
                    "WhatsApp connection not found"));
    synchronize(channelConnection);
    return broadcastSenderRepository
        .findByIdAndCompanyId(senderId, companyId)
        .orElseThrow(
            () -> new com.messaging.common.exception.NotFoundException(
                "Broadcast sender not found"));
  }

  private void synchronize(ChannelConnection channelConnection) {
    long companyId = channelConnection.getCompanyId();
    Optional<MetaWhatsAppControlPlaneProjectionRepository.Projection> projectionOptional =
        projectionRepository.findByConnectionId(channelConnection.getId());
    MetaWhatsAppControlPlaneProjectionRepository.Projection projection =
        projectionOptional.orElse(null);

    SenderIdentity identity = resolveIdentity(channelConnection.getId(), projection).orElse(null);
    boolean disconnected =
        channelConnection.getStatus() == ChannelConnectionStatus.DISCONNECTED;
    boolean usable =
        !disconnected
            && channelConnection.getStatus() == ChannelConnectionStatus.ACTIVE
            && projection != null
            && projection.usableForSend()
            && identity != null
            && identity.getStatus() == SenderIdentityStatus.ACTIVE;

    BroadcastSenderConnection adapter =
        broadcastConnectionRepository
            .findByChannelConnectionIdAndCompanyId(channelConnection.getId(), companyId)
            .orElseGet(BroadcastSenderConnection::new);
    if (adapter.getId() == null) {
      adapter.setId(IdGenerator.nextId());
      adapter.setCompanyId(companyId);
      adapter.setChannelConnectionId(channelConnection.getId());
      adapter.setProvider(BroadcastSenderProvider.META_WHATSAPP);
      adapter.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
      adapter.setAuthMode(BroadcastSenderAuthMode.API_KEY);
      adapter.setRegion("META");
      adapter.setCreatedByUserId(channelConnection.getCreatedByUserId());
    }
    adapter.setName(channelConnection.getName());
    adapter.setStatus(
        disconnected
            ? BroadcastSenderConnectionStatus.DISABLED
            : usable
                ? BroadcastSenderConnectionStatus.ACTIVE
                : BroadcastSenderConnectionStatus.ERROR);
    adapter.setProductionAccessEnabled(usable);
    adapter.setSendingEnabled(usable);
    adapter.setLastValidatedAt(projection == null ? null : projection.lastValidatedAt());
    adapter.setEncryptedCredentials(null);
    adapter.setRoleArn(null);
    adapter.setExternalId(projection == null ? null : projection.selectedPhoneNumberId());
    adapter = broadcastConnectionRepository.save(adapter);

    List<BroadcastSender> existing =
        broadcastSenderRepository.findByConnectionId(adapter.getId());
    BroadcastSender sender = existing.isEmpty() ? null : existing.getFirst();
    if (identity == null) {
      if (sender != null && disconnected) {
        sender.setStatus(BroadcastSenderStatus.DISABLED);
        broadcastSenderRepository.save(sender);
      }
      return;
    }

    if (sender == null) {
      sender =
          broadcastSenderRepository
              .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
                  companyId, BroadcastSenderProvider.META_WHATSAPP, identity.getValue())
              .orElseGet(BroadcastSender::new);
    }
    if (sender.getId() == null) sender.setId(IdGenerator.nextId());
    sender.setCompanyId(companyId);
    sender.setConnectionId(adapter.getId());
    sender.setProvider(BroadcastSenderProvider.META_WHATSAPP);
    sender.setFromAddress(identity.getValue());
    sender.setDisplayName(channelConnection.getName());
    sender.setReplyToAddress(null);
    sender.setVerifiedIdentity(
        projection != null && projection.selectedPhoneNumberId() != null
            ? projection.selectedPhoneNumberId()
            : identity.getExternalId());
    sender.setStatus(disconnected ? BroadcastSenderStatus.DISABLED : BroadcastSenderStatus.ACTIVE);
    broadcastSenderRepository.save(sender);
  }

  private Optional<SenderIdentity> resolveIdentity(
      long connectionId, MetaWhatsAppControlPlaneProjectionRepository.Projection projection) {
    if (projection != null && projection.selectedPhoneNumberId() != null) {
      Optional<SenderIdentity> exact =
          senderIdentityRepository.findByConnectionIdAndIdentityTypeAndExternalId(
              connectionId,
              SenderIdentityType.PHONE_NUMBER,
              projection.selectedPhoneNumberId());
      if (exact.isPresent()) return exact;
    }
    return senderIdentityRepository.findFirstByConnectionIdAndDefaultIdentityTrue(connectionId);
  }
}
