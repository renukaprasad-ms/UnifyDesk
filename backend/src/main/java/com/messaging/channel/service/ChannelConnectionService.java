package com.messaging.channel.service;

import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.dto.CreateChannelConnectionRequest;
import com.messaging.channel.dto.CreateSenderIdentityRequest;
import com.messaging.channel.dto.SenderIdentityResponse;
import com.messaging.channel.dto.UpdateChannelConnectionRequest;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.provider.ChannelProviderRegistry;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelConnectionService {

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ChannelEntitlementService entitlementService;
  private final ProviderCredentialService credentialService;
  private final ChannelProviderRegistry providerRegistry;

  @Transactional
  public ChannelConnectionResponse create(
      Long companyId, Long userId, CreateChannelConnectionRequest request) {
    validateProvider(request.channelType(), request.providerType());
    entitlementService.assertCanCreateConnection(companyId, request.channelType());

    String externalAccountId = normalizeNullable(request.externalAccountId());
    if (externalAccountId != null
        && connectionRepository.existsByCompanyIdAndProviderTypeAndExternalAccountId(
            companyId, request.providerType(), externalAccountId)) {
      throw new BadRequestException("This provider account is already connected to the company");
    }

    ChannelConnection connection = new ChannelConnection();
    connection.setCompanyId(companyId);
    connection.setChannelType(request.channelType());
    connection.setProviderType(request.providerType());
    connection.setName(request.name().trim());
    connection.setExternalAccountId(externalAccountId);
    connection.setStatus(ChannelConnectionStatus.PENDING);
    connection.setCreatedByUserId(userId);
    return toResponse(connectionRepository.save(connection));
  }

  @Transactional(readOnly = true)
  public List<ChannelConnectionResponse> list(Long companyId, ChannelType channelType) {
    List<ChannelConnection> connections =
        channelType == null
            ? connectionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
            : connectionRepository.findByCompanyIdAndChannelTypeOrderByCreatedAtDesc(
                companyId, channelType);
    return connections.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public ChannelConnectionResponse get(Long companyId, Long connectionId) {
    return toResponse(requireConnection(companyId, connectionId));
  }

  @Transactional
  public ChannelConnectionResponse update(
      Long companyId, Long connectionId, UpdateChannelConnectionRequest request) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    connection.setName(request.name().trim());
    return toResponse(connectionRepository.save(connection));
  }

  @Transactional
  public ChannelConnectionResponse disconnect(Long companyId, Long connectionId) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    connection.setStatus(ChannelConnectionStatus.DISCONNECTED);
    credentialService.deleteAll(connection.getId());
    return toResponse(connectionRepository.save(connection));
  }

  @Transactional
  public SenderIdentityResponse addSender(
      Long companyId, Long connectionId, CreateSenderIdentityRequest request) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    if (connection.getStatus() == ChannelConnectionStatus.DISCONNECTED) {
      throw new BadRequestException("Cannot add a sender to a disconnected channel");
    }
    validateIdentityType(connection.getChannelType(), request.identityType());
    entitlementService.assertCanCreateSender(companyId, request.identityType());

    List<SenderIdentity> existing =
        senderIdentityRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId);
    boolean makeDefault = request.defaultIdentity() || existing.isEmpty();
    if (makeDefault) {
      existing.forEach(sender -> sender.setDefaultIdentity(false));
      senderIdentityRepository.saveAll(existing);
    }

    SenderIdentity sender = new SenderIdentity();
    sender.setConnection(connection);
    sender.setIdentityType(request.identityType());
    sender.setValue(request.value().trim());
    sender.setExternalId(normalizeNullable(request.externalId()));
    sender.setStatus(SenderIdentityStatus.PENDING);
    sender.setDefaultIdentity(makeDefault);
    return toResponse(senderIdentityRepository.save(sender));
  }

  @Transactional(readOnly = true)
  public List<SenderIdentityResponse> listSenders(Long companyId, Long connectionId) {
    requireConnection(companyId, connectionId);
    return senderIdentityRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public SenderIdentityResponse makeDefaultSender(
      Long companyId, Long connectionId, Long senderIdentityId) {
    requireConnection(companyId, connectionId);
    SenderIdentity selected =
        senderIdentityRepository
            .findByIdAndConnectionId(senderIdentityId, connectionId)
            .orElseThrow(() -> new NotFoundException("Sender identity not found"));

    List<SenderIdentity> senders =
        senderIdentityRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId);
    for (SenderIdentity sender : senders) {
      sender.setDefaultIdentity(sender.getId().equals(selected.getId()));
    }
    senderIdentityRepository.saveAll(senders);
    return toResponse(selected);
  }

  @Transactional
  public ChannelConnection markActive(
      Long companyId, Long connectionId, String externalAccountId, Instant lastSyncAt) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    String normalizedExternalId = normalizeNullable(externalAccountId);
    if (normalizedExternalId != null
        && connectionRepository.existsByCompanyIdAndProviderTypeAndExternalAccountId(
            companyId, connection.getProviderType(), normalizedExternalId)
        && !normalizedExternalId.equals(connection.getExternalAccountId())) {
      throw new BadRequestException("This provider account is already connected to the company");
    }
    connection.setExternalAccountId(normalizedExternalId);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection.setLastSyncAt(lastSyncAt == null ? Instant.now() : lastSyncAt);
    return connectionRepository.save(connection);
  }

  @Transactional
  public ChannelConnection markError(Long companyId, Long connectionId) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    connection.setStatus(ChannelConnectionStatus.ERROR);
    return connectionRepository.save(connection);
  }

  private ChannelConnection requireConnection(Long companyId, Long connectionId) {
    return connectionRepository
        .findByIdAndCompanyId(connectionId, companyId)
        .orElseThrow(() -> new NotFoundException("Channel connection not found"));
  }

  private void validateProvider(ChannelType channelType, com.messaging.channel.enums.ProviderType providerType) {
    if (!providerType.supports(channelType)) {
      throw new BadRequestException(
          "Provider " + providerType + " does not support channel " + channelType);
    }
  }

  private void validateIdentityType(ChannelType channelType, SenderIdentityType identityType) {
    boolean valid =
        switch (channelType) {
          case EMAIL -> identityType == SenderIdentityType.EMAIL_ADDRESS;
          case WHATSAPP -> identityType == SenderIdentityType.PHONE_NUMBER;
          case SMS -> identityType == SenderIdentityType.SMS_HEADER;
        };
    if (!valid) {
      throw new BadRequestException(
          "Sender identity type " + identityType + " is invalid for channel " + channelType);
    }
  }

  private ChannelConnectionResponse toResponse(ChannelConnection connection) {
    return new ChannelConnectionResponse(
        connection.getId(),
        connection.getCompanyId(),
        connection.getChannelType(),
        connection.getProviderType(),
        connection.getName(),
        connection.getExternalAccountId(),
        connection.getStatus(),
        connection.getCreatedByUserId(),
        connection.getLastSyncAt(),
        connection.getCreatedAt(),
        connection.getUpdatedAt(),
        providerRegistry.isConfigured(connection.getProviderType()));
  }

  private SenderIdentityResponse toResponse(SenderIdentity sender) {
    return new SenderIdentityResponse(
        sender.getId(),
        sender.getConnection().getId(),
        sender.getIdentityType(),
        sender.getValue(),
        sender.getExternalId(),
        sender.getStatus(),
        sender.isDefaultIdentity(),
        sender.getCreatedAt(),
        sender.getUpdatedAt());
  }

  private String normalizeNullable(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
