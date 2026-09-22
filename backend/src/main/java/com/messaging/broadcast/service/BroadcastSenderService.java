package com.messaging.broadcast.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.broadcast.ses.*;
import com.messaging.channel.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastSenderService {

  private final BroadcastSenderRepository senderRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final BroadcastSenderPersistenceService persistenceService;
  private final com.messaging.channel.service.ChannelEntitlementService channelEntitlementService;
  private final com.messaging.channel.service.ChannelConnectionService channelConnectionService;
  private final AmazonSesGateway sesGateway;
  private final AmazonSesClientManager sesClientManager;
  private final CredentialCipher credentialCipher;
  private final ObjectMapper objectMapper;

  public BroadcastSenderResponse connectAmazonSes(
      Long companyId, Long userId, ConnectAmazonSesRequest request) {
    String fromAddress = normalizeEmail(request.fromAddress(), "From address");
    String replyTo =
        request.replyToAddress() == null || request.replyToAddress().isBlank()
            ? null
            : normalizeEmail(request.replyToAddress(), "Reply-To address");

    senderRepository
        .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            companyId, BroadcastSenderProvider.AMAZON_SES, fromAddress)
        .ifPresent(
            existing -> {
              throw new BadRequestException(
                  "This Amazon SES sender is already connected to the company");
            });

    channelEntitlementService.assertCanCreateConnection(
        companyId, com.messaging.channel.enums.ChannelType.EMAIL);
    validateAuth(request);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(IdGenerator.nextId());
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setAuthMode(request.authMode());
    connection.setName(request.name().trim());
    connection.setRegion(request.region().trim());
    connection.setRoleArn(trimToNull(request.roleArn()));
    connection.setExternalId(trimToNull(request.externalId()));
    connection.setCreatedByUserId(userId);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    if (request.authMode() == BroadcastSenderAuthMode.ACCESS_KEYS) {
      connection.setEncryptedCredentials(
          encryptCredentials(request.accessKeyId(), request.secretAccessKey()));
    }

    try {
      AmazonSesAccountSnapshot snapshot = sesGateway.inspect(connection, fromAddress);
      if (!snapshot.sendingEnabled()) {
        throw new BadRequestException("Amazon SES sending is disabled for this account/region");
      }
      applySnapshot(connection, snapshot);

      BroadcastSender sender = new BroadcastSender();
      sender.setCompanyId(companyId);
      sender.setConnectionId(connection.getId());
      sender.setProvider(BroadcastSenderProvider.AMAZON_SES);
      sender.setFromAddress(fromAddress);
      sender.setDisplayName(trimToNull(request.displayName()));
      sender.setReplyToAddress(replyTo);
      sender.setVerifiedIdentity(snapshot.verifiedIdentity());
      sender.setStatus(BroadcastSenderStatus.ACTIVE);
      return persistenceService.create(connection, sender);
    } catch (RuntimeException exception) {
      sesClientManager.invalidate(connection.getId());
      throw exception;
    }
  }

  @Transactional(readOnly = true)
  public List<BroadcastSenderResponse> list(Long companyId) {
    return senderRepository
        .findByCompanyIdAndStatusOrderByUpdatedAtDesc(companyId, BroadcastSenderStatus.ACTIVE)
        .stream()
        .map(
            sender ->
                BroadcastSenderResponse.from(
                    sender,
                    connectionRepository
                        .findByIdAndCompanyId(sender.getConnectionId(), companyId)
                        .orElseThrow(
                            () -> new NotFoundException("Broadcast sender connection not found"))))
        .toList();
  }

  public BroadcastSenderResponse refresh(Long companyId, Long senderId) {
    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(senderId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast sender not found"));
    if (sender.getStatus() == BroadcastSenderStatus.DISABLED) {
      throw new BadRequestException("Broadcast sender is disconnected");
    }
    BroadcastSenderConnection connection = requireConnection(companyId, sender.getConnectionId());
    AmazonSesAccountSnapshot snapshot = sesGateway.inspect(connection, sender.getFromAddress());
    applySnapshot(connection, snapshot);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connectionRepository.save(connection);
    return BroadcastSenderResponse.from(sender, connection);
  }

  public void disable(Long companyId, Long senderId, Long userId) {
    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(senderId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast sender not found"));
    BroadcastSenderConnection connection = requireConnection(companyId, sender.getConnectionId());
    if (connection.getChannelConnectionId() == null) {
      throw new BadRequestException("Legacy Broadcast sender cannot be disconnected as a channel");
    }
    channelConnectionService.disconnect(
        companyId,
        connection.getChannelConnectionId(),
        userId,
        com.messaging.channel.enums.DisconnectDataPolicy.DELETE_NOW);
  }

  @Transactional(readOnly = true)
  public BroadcastSender requireActive(Long companyId, Long senderId) {
    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(senderId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast sender not found"));
    if (sender.getStatus() != BroadcastSenderStatus.ACTIVE) {
      throw new BadRequestException("Broadcast sender is not active");
    }
    BroadcastSenderConnection connection = requireConnection(companyId, sender.getConnectionId());
    if (connection.getStatus() != BroadcastSenderConnectionStatus.ACTIVE
        || !connection.isSendingEnabled()) {
      throw new BadRequestException("Broadcast sender connection is not active");
    }
    return sender;
  }

  @Transactional(readOnly = true)
  public BroadcastSenderConnection requireConnection(Long companyId, Long connectionId) {
    return connectionRepository
        .findByIdAndCompanyId(connectionId, companyId)
        .orElseThrow(() -> new NotFoundException("Broadcast sender connection not found"));
  }

  private void validateAuth(ConnectAmazonSesRequest request) {
    if (request.authMode() == BroadcastSenderAuthMode.ACCESS_KEYS) {
      if (isBlank(request.accessKeyId()) || isBlank(request.secretAccessKey())) {
        throw new BadRequestException(
            "AWS access key id and secret access key are required");
      }
      return;
    }
    if (isBlank(request.roleArn())) {
      throw new BadRequestException("IAM role ARN is required");
    }
  }

  private String encryptCredentials(String accessKeyId, String secretAccessKey) {
    try {
      String payload =
          objectMapper.writeValueAsString(
              new SesStoredCredentials(accessKeyId.trim(), secretAccessKey.trim()));
      return credentialCipher.encrypt(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encrypt Amazon SES credentials", exception);
    }
  }

  private void applySnapshot(
      BroadcastSenderConnection connection, AmazonSesAccountSnapshot snapshot) {
    connection.setProductionAccessEnabled(snapshot.productionAccessEnabled());
    connection.setSendingEnabled(snapshot.sendingEnabled());
    connection.setMax24HourSend(snapshot.max24HourSend());
    connection.setMaxSendRate(snapshot.maxSendRate());
    connection.setSentLast24Hours(snapshot.sentLast24Hours());
    connection.setLastValidatedAt(Instant.now());
  }

  private String normalizeEmail(String value, String label) {
    try {
      InternetAddress address = new InternetAddress(value.trim(), true);
      address.validate();
      return address.getAddress().trim().toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      throw new BadRequestException(label + " is invalid");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }
}
