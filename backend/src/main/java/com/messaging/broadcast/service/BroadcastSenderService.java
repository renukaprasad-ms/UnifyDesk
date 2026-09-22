package com.messaging.broadcast.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.azure.*;
import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.msg91.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.broadcast.ses.*;
import com.messaging.broadcast.sendgrid.*;
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
  private final BroadcastSetupSessionService setupSessionService;
  private final com.messaging.channel.service.ChannelEntitlementService channelEntitlementService;
  private final com.messaging.channel.service.ChannelConnectionService channelConnectionService;
  private final AmazonSesGateway sesGateway;
  private final AmazonSesClientManager sesClientManager;
  private final SendGridGateway sendGridGateway;
  private final AzureEmailGateway azureEmailGateway;
  private final Msg91Gateway msg91Gateway;
  private final Msg91EmailGateway msg91EmailGateway;
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
      return persistAndCompleteSetup(connection, sender);
    } catch (RuntimeException exception) {
      sesClientManager.invalidate(connection.getId());
      throw exception;
    }
  }

  public BroadcastSenderResponse connectSendGrid(
      Long companyId, Long userId, ConnectSendGridRequest request) {
    String fromAddress = normalizeEmail(request.fromAddress(), "From address");
    String replyTo =
        request.replyToAddress() == null || request.replyToAddress().isBlank()
            ? null
            : normalizeEmail(request.replyToAddress(), "Reply-To address");

    senderRepository
        .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            companyId, BroadcastSenderProvider.SENDGRID, fromAddress)
        .ifPresent(
            existing -> {
              throw new BadRequestException(
                  "This SendGrid sender is already connected to the company");
            });

    channelEntitlementService.assertCanCreateConnection(
        companyId, com.messaging.channel.enums.ChannelType.EMAIL);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(IdGenerator.nextId());
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.SENDGRID);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setAuthMode(BroadcastSenderAuthMode.API_KEY);
    connection.setName(request.name().trim());
    connection.setRegion(request.region().name());
    connection.setCreatedByUserId(userId);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setEncryptedCredentials(encryptSendGridApiKey(request.apiKey()));

    SendGridAccountSnapshot snapshot = sendGridGateway.inspect(connection, fromAddress);
    applySendGridSnapshot(connection, snapshot);

    BroadcastSender sender = new BroadcastSender();
    sender.setCompanyId(companyId);
    sender.setConnectionId(connection.getId());
    sender.setProvider(BroadcastSenderProvider.SENDGRID);
    sender.setFromAddress(fromAddress);
    sender.setDisplayName(trimToNull(request.displayName()));
    sender.setReplyToAddress(replyTo);
    sender.setVerifiedIdentity(snapshot.verifiedIdentity());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    return persistAndCompleteSetup(connection, sender);
  }

  public BroadcastSenderResponse connectAzureEmail(
      Long companyId, Long userId, ConnectAzureEmailRequest request) {
    String fromAddress = normalizeEmail(request.fromAddress(), "From address");
    String replyTo =
        request.replyToAddress() == null || request.replyToAddress().isBlank()
            ? null
            : normalizeEmail(request.replyToAddress(), "Reply-To address");

    senderRepository
        .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            companyId, BroadcastSenderProvider.AZURE_EMAIL, fromAddress)
        .ifPresent(
            existing -> {
              throw new BadRequestException(
                  "This Azure Email sender is already connected to the company");
            });

    channelEntitlementService.assertCanCreateConnection(
        companyId, com.messaging.channel.enums.ChannelType.EMAIL);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(IdGenerator.nextId());
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.AZURE_EMAIL);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setAuthMode(BroadcastSenderAuthMode.API_KEY);
    connection.setName(request.name().trim());
    connection.setRegion("AZURE");
    connection.setCreatedByUserId(userId);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setProviderConfigJson(azureProviderConfig(request.endpoint()));
    connection.setEncryptedCredentials(encryptAzureAccessKey(request.accessKey()));

    AzureEmailAccountSnapshot snapshot = azureEmailGateway.inspect(connection, fromAddress);
    applyAzureSnapshot(connection, snapshot);

    BroadcastSender sender = new BroadcastSender();
    sender.setCompanyId(companyId);
    sender.setConnectionId(connection.getId());
    sender.setProvider(BroadcastSenderProvider.AZURE_EMAIL);
    sender.setFromAddress(fromAddress);
    sender.setDisplayName(trimToNull(request.displayName()));
    sender.setReplyToAddress(replyTo);
    sender.setVerifiedIdentity(snapshot.verifiedIdentity());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    return persistAndCompleteSetup(connection, sender);
  }

  public BroadcastSenderResponse connectMsg91Sms(
      Long companyId, Long userId, ConnectMsg91Request request) {
    String senderId = request.senderId().trim().toUpperCase(Locale.ROOT);
    if (senderId.isBlank()) {
      throw new BadRequestException("MSG91 Sender ID is required");
    }

    senderRepository
        .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            companyId, BroadcastSenderProvider.MSG91_SMS, senderId)
        .ifPresent(
            existing -> {
              throw new BadRequestException(
                  "This MSG91 Sender ID is already connected to the company");
            });

    channelEntitlementService.assertCanCreateConnection(
        companyId, com.messaging.channel.enums.ChannelType.SMS);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(IdGenerator.nextId());
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setAuthMode(BroadcastSenderAuthMode.API_KEY);
    connection.setName(request.name().trim());
    connection.setRegion("MSG91");
    connection.setExternalId(request.flowId().trim());
    connection.setCreatedByUserId(userId);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setProviderConfigJson(
        msg91ProviderConfig(request.flowId(), request.messageVariable(), request.route()));
    connection.setEncryptedCredentials(encryptMsg91AuthKey(request.authKey()));

    Msg91AccountSnapshot snapshot = msg91Gateway.inspect(connection, senderId);
    applyMsg91Snapshot(connection, snapshot);

    BroadcastSender sender = new BroadcastSender();
    sender.setCompanyId(companyId);
    sender.setConnectionId(connection.getId());
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);
    sender.setFromAddress(senderId);
    sender.setDisplayName(trimToNull(request.name()));
    sender.setReplyToAddress(null);
    sender.setVerifiedIdentity(snapshot.verifiedIdentity());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    return persistAndCompleteSetup(connection, sender);
  }

  public BroadcastSenderResponse connectMsg91Email(
      Long companyId, Long userId, ConnectMsg91EmailRequest request) {
    String fromAddress = normalizeEmail(request.fromAddress(), "From address");
    String replyTo =
        request.replyToAddress() == null || request.replyToAddress().isBlank()
            ? null
            : normalizeEmail(request.replyToAddress(), "Reply-To address");

    senderRepository
        .findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            companyId, BroadcastSenderProvider.MSG91_EMAIL, fromAddress)
        .ifPresent(
            existing -> {
              throw new BadRequestException(
                  "This MSG91 Email sender is already connected to the company");
            });

    channelEntitlementService.assertCanCreateConnection(
        companyId, com.messaging.channel.enums.ChannelType.EMAIL);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(IdGenerator.nextId());
    connection.setCompanyId(companyId);
    connection.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    connection.setManagementMode(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    connection.setAuthMode(BroadcastSenderAuthMode.API_KEY);
    connection.setName(request.name().trim());
    connection.setRegion("MSG91_EMAIL");
    connection.setExternalId(request.templateId().trim());
    connection.setCreatedByUserId(userId);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setProviderConfigJson(
        msg91EmailProviderConfig(
            request.domain(),
            request.templateId(),
            request.messageVariable(),
            request.subjectVariable()));
    connection.setEncryptedCredentials(encryptMsg91AuthKey(request.authKey()));

    Msg91EmailAccountSnapshot snapshot = msg91EmailGateway.inspect(connection, fromAddress);
    applyMsg91EmailSnapshot(connection, snapshot);

    BroadcastSender sender = new BroadcastSender();
    sender.setCompanyId(companyId);
    sender.setConnectionId(connection.getId());
    sender.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    sender.setFromAddress(fromAddress);
    sender.setDisplayName(trimToNull(request.displayName()));
    sender.setReplyToAddress(replyTo);
    sender.setVerifiedIdentity(snapshot.verifiedIdentity());
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    return persistAndCompleteSetup(connection, sender);
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
    switch (sender.getProvider()) {
      case AMAZON_SES -> {
        AmazonSesAccountSnapshot snapshot = sesGateway.inspect(connection, sender.getFromAddress());
        applySnapshot(connection, snapshot);
        sender.setVerifiedIdentity(snapshot.verifiedIdentity());
      }
      case SENDGRID -> {
        SendGridAccountSnapshot snapshot =
            sendGridGateway.inspect(connection, sender.getFromAddress());
        applySendGridSnapshot(connection, snapshot);
        sender.setVerifiedIdentity(snapshot.verifiedIdentity());
      }
      case AZURE_EMAIL -> {
        AzureEmailAccountSnapshot snapshot =
            azureEmailGateway.inspect(connection, sender.getFromAddress());
        applyAzureSnapshot(connection, snapshot);
        sender.setVerifiedIdentity(snapshot.verifiedIdentity());
      }
      case MSG91_SMS -> {
        Msg91AccountSnapshot snapshot =
            msg91Gateway.inspect(connection, sender.getFromAddress());
        applyMsg91Snapshot(connection, snapshot);
        sender.setVerifiedIdentity(snapshot.verifiedIdentity());
      }
      case MSG91_EMAIL -> {
        Msg91EmailAccountSnapshot snapshot =
            msg91EmailGateway.inspect(connection, sender.getFromAddress());
        applyMsg91EmailSnapshot(connection, snapshot);
        sender.setVerifiedIdentity(snapshot.verifiedIdentity());
      }
      case GMAIL, MICROSOFT, IMAP_SMTP, SMTP ->
          throw new BadRequestException("Mailbox providers cannot be refreshed as Broadcast senders");
    }
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connectionRepository.save(connection);
    senderRepository.save(sender);
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

  private BroadcastSenderResponse persistAndCompleteSetup(
      BroadcastSenderConnection connection, BroadcastSender sender) {
    BroadcastSenderResponse response = persistenceService.create(connection, sender);
    setupSessionService.completeActive(connection.getCompanyId(), connection.getProvider());
    return response;
  }

  private void validateAuth(ConnectAmazonSesRequest request) {
    if (request.authMode() == BroadcastSenderAuthMode.ACCESS_KEYS) {
      if (isBlank(request.accessKeyId()) || isBlank(request.secretAccessKey())) {
        throw new BadRequestException(
            "AWS access key id and secret access key are required");
      }
      return;
    }
    if (request.authMode() == BroadcastSenderAuthMode.IAM_ROLE) {
      if (isBlank(request.roleArn())) {
        throw new BadRequestException("IAM role ARN is required");
      }
      return;
    }
    throw new BadRequestException("Amazon SES supports IAM_ROLE or ACCESS_KEYS authentication");
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

  private String encryptSendGridApiKey(String apiKey) {
    if (isBlank(apiKey)) {
      throw new BadRequestException("SendGrid API key is required");
    }
    try {
      String payload =
          objectMapper.writeValueAsString(new SendGridStoredCredentials(apiKey.trim()));
      return credentialCipher.encrypt(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encrypt SendGrid credentials", exception);
    }
  }

  private String encryptMsg91AuthKey(String authKey) {
    if (isBlank(authKey)) {
      throw new BadRequestException("MSG91 auth key is required");
    }
    try {
      String payload =
          objectMapper.writeValueAsString(new Msg91StoredCredentials(authKey.trim()));
      return credentialCipher.encrypt(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encrypt MSG91 credentials", exception);
    }
  }

  private String msg91ProviderConfig(
      String flowId, String messageVariable, String route) {
    String variable =
        isBlank(messageVariable) ? "VAR1" : messageVariable.trim();
    try {
      return objectMapper.writeValueAsString(
          new Msg91ProviderConfig(
              flowId.trim(), variable, isBlank(route) ? null : route.trim()));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize MSG91 configuration", exception);
    }
  }

  private String msg91EmailProviderConfig(
      String domain,
      String templateId,
      String messageVariable,
      String subjectVariable) {
    String bodyVariable =
        isBlank(messageVariable) ? "message" : messageVariable.trim();
    try {
      return objectMapper.writeValueAsString(
          new Msg91EmailProviderConfig(
              domain.trim().toLowerCase(Locale.ROOT),
              templateId.trim(),
              bodyVariable,
              isBlank(subjectVariable) ? null : subjectVariable.trim()));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize MSG91 Email configuration", exception);
    }
  }

  private void applyMsg91EmailSnapshot(
      BroadcastSenderConnection connection, Msg91EmailAccountSnapshot snapshot) {
    connection.setProductionAccessEnabled(true);
    connection.setSendingEnabled(snapshot.sendingEnabled());
    connection.setMax24HourSend(null);
    connection.setMaxSendRate(snapshot.maxSendRate());
    connection.setSentLast24Hours(null);
    connection.setLastValidatedAt(Instant.now());
  }

  private void applyMsg91Snapshot(
      BroadcastSenderConnection connection, Msg91AccountSnapshot snapshot) {
    connection.setProductionAccessEnabled(true);
    connection.setSendingEnabled(snapshot.sendingEnabled());
    connection.setMax24HourSend(null);
    connection.setMaxSendRate(snapshot.maxSendRate());
    connection.setSentLast24Hours(null);
    connection.setLastValidatedAt(Instant.now());
  }

  private String encryptAzureAccessKey(String accessKey) {
    if (isBlank(accessKey)) {
      throw new BadRequestException("Azure Email access key is required");
    }
    try {
      String payload =
          objectMapper.writeValueAsString(new AzureEmailStoredCredentials(accessKey.trim()));
      return credentialCipher.encrypt(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encrypt Azure Email credentials", exception);
    }
  }

  private String azureProviderConfig(String endpoint) {
    if (isBlank(endpoint)) {
      throw new BadRequestException("Azure Email endpoint is required");
    }
    try {
      return objectMapper.writeValueAsString(new AzureEmailProviderConfig(endpoint.trim()));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize Azure Email configuration", exception);
    }
  }

  private void applyAzureSnapshot(
      BroadcastSenderConnection connection, AzureEmailAccountSnapshot snapshot) {
    connection.setProductionAccessEnabled(true);
    connection.setSendingEnabled(snapshot.sendingEnabled());
    connection.setMax24HourSend(null);
    connection.setMaxSendRate(snapshot.maxSendRate());
    connection.setSentLast24Hours(null);
    connection.setLastValidatedAt(Instant.now());
  }

  private void applySendGridSnapshot(
      BroadcastSenderConnection connection, SendGridAccountSnapshot snapshot) {
    connection.setProductionAccessEnabled(true);
    connection.setSendingEnabled(snapshot.sendingEnabled());
    connection.setMax24HourSend(null);
    connection.setMaxSendRate(snapshot.maxSendRate());
    connection.setSentLast24Hours(null);
    connection.setLastValidatedAt(Instant.now());
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
