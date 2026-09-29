package com.messaging.channel.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.control.MetaGraphException;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.CoexistenceState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation.OperationState;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation.OperationType;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation.ProvisioningStep;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperationRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppPersistenceService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ProviderCredentialService credentialService;
  private final MetaWhatsAppConnectionControlRepository controlRepository;
  private final MetaWhatsAppProvisioningOperationRepository operationRepository;
  private final MetaWhatsAppProperties properties;
  private final ObjectMapper objectMapper;

  @Transactional
  public PendingProvisioning persistPending(
      Long companyId,
      Long userId,
      Long expectedConnectionId,
      String wabaId,
      String whatsappAccountId,
      String messagingAccountId,
      WabaDetails waba,
      PhoneNumber phone,
      TokenResponse token,
      Instant tokenExpiresAt,
      OperationType operationType) {
    ChannelConnection connection =
        resolveConnection(companyId, expectedConnectionId, wabaId).orElseGet(ChannelConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.WHATSAPP);
      connection.setProviderType(ProviderType.META_WHATSAPP);
      connection.setCreatedByUserId(userId);
    } else if (connection.getProviderType() != ProviderType.META_WHATSAPP) {
      throw new BadRequestException("Channel connection is not a Meta WhatsApp connection");
    }

    connection.setExternalAccountId(wabaId);
    connection.setName(
        waba.name() == null || waba.name().isBlank() ? "WhatsApp Business" : waba.name().trim());
    connection.setStatus(ChannelConnectionStatus.PENDING);
    connection.setDisconnectedAt(null);
    connection.setPurgeAfter(null);
    connection.setDeletionRequestedByUserId(null);
    connection = connectionRepository.saveAndFlush(connection);

    String pin = stablePin(connection.getId());

    credentialService.upsert(
        connection,
        CredentialType.OAUTH_TOKEN_SET,
        json(
            new StoredMetaToken(
                token.accessToken(),
                wabaId,
                phone.id(),
                properties.getGraphApiVersion(),
                whatsappAccountId,
                messagingAccountId)),
        tokenExpiresAt);

    if (properties.isRegisterPhoneNumber()) {
      credentialService.upsert(
          connection, CredentialType.PROVIDER_SECRET, json(new StoredMetaSecret(pin)), null);
    }

    SenderIdentity sender =
        senderIdentityRepository
            .findByConnectionIdAndIdentityTypeAndExternalId(
                connection.getId(), SenderIdentityType.PHONE_NUMBER, phone.id())
            .orElseGet(SenderIdentity::new);
    if (sender.getId() == null) {
      sender.setConnection(connection);
      sender.setIdentityType(SenderIdentityType.PHONE_NUMBER);
      sender.setDefaultIdentity(
          senderIdentityRepository
              .findFirstByConnectionIdAndDefaultIdentityTrue(connection.getId())
              .isEmpty());
    }
    sender.setExternalId(phone.id());
    sender.setValue(displayValue(phone));
    sender.setStatus(SenderIdentityStatus.PENDING);
    sender = senderIdentityRepository.saveAndFlush(sender);

    MetaWhatsAppConnectionControl control =
        controlRepository.findById(connection.getId()).orElseGet(MetaWhatsAppConnectionControl::new);
    boolean alreadyRegistered = control.getRegistrationState() == RegistrationState.REGISTERED;
    boolean alreadySubscribed = control.getSubscriptionState() == SubscriptionState.SUBSCRIBED;
    control.setConnectionId(connection.getId());
    control.setCompanyId(companyId);
    control.setLegacyWabaId(wabaId);
    control.setWhatsappAccountId(normalize(whatsappAccountId));
    control.setMessagingAccountId(normalize(messagingAccountId));
    control.setSelectedPhoneNumberId(phone.id());
    control.setProductState(ProductState.VALIDATING);
    control.setAuthState(AuthState.VALID);
    control.setRegistrationState(
        alreadyRegistered || !properties.isRegisterPhoneNumber()
            ? RegistrationState.REGISTERED
            : RegistrationState.PENDING);
    control.setSubscriptionState(
        alreadySubscribed ? SubscriptionState.SUBSCRIBED : SubscriptionState.PENDING);
    control.setCoexistenceEnabled(false);
    control.setCoexistenceState(CoexistenceState.DISABLED);
    control.setLastSafeErrorCode(null);
    control.setNextReconcileAt(Instant.now());
    try {
      control = controlRepository.saveAndFlush(control);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(
          "WHATSAPP_PHONE_OWNED_BY_OTHER_COMPANY: selected phone is already connected");
    }

    MetaWhatsAppProvisioningOperation operation =
        operationRepository
            .findByOperationKey("meta-whatsapp:" + connection.getId())
            .orElseGet(MetaWhatsAppProvisioningOperation::new);
    operation.setConnectionId(connection.getId());
    operation.setCompanyId(companyId);
    operation.setOperationKey("meta-whatsapp:" + connection.getId());
    operation.setOperationType(operationType);
    operation.setState(OperationState.PENDING);
    operation.setStep(ProvisioningStep.LOCAL_PERSISTED);
    operation.setNextAttemptAt(Instant.now());
    operation.setCompletedAt(null);
    operation.setLastSafeErrorCode(null);
    operationRepository.save(operation);

    return new PendingProvisioning(
        connection.getId(),
        sender.getId(),
        pin,
        wabaId,
        phone.id(),
        sender.getValue(),
        phone.verifiedName(),
        phone.qualityRating());
  }

  @Transactional
  public void markRegistrationSucceeded(Long connectionId) {
    MetaWhatsAppConnectionControl control = requireControl(connectionId);
    control.setRegistrationState(RegistrationState.REGISTERED);
    control.setLastSafeErrorCode(null);
    controlRepository.save(control);
    updateOperation(connectionId, ProvisioningStep.PHONE_REGISTERED, OperationState.RUNNING, null);
  }

  @Transactional
  public void markSubscriptionSucceeded(Long connectionId) {
    MetaWhatsAppConnectionControl control = requireControl(connectionId);
    control.setSubscriptionState(SubscriptionState.SUBSCRIBED);
    control.setLastSafeErrorCode(null);
    controlRepository.save(control);
    updateOperation(connectionId, ProvisioningStep.APP_SUBSCRIBED, OperationState.RUNNING, null);
  }

  @Transactional
  public void markHealthy(Long connectionId) {
    Instant now = Instant.now();
    MetaWhatsAppConnectionControl control = requireControl(connectionId);
    control.setProductState(ProductState.ACTIVE);
    control.setAuthState(AuthState.VALID);
    control.setRegistrationState(RegistrationState.REGISTERED);
    control.setSubscriptionState(SubscriptionState.SUBSCRIBED);
    control.setLastValidatedAt(now);
    control.setNextReconcileAt(now.plus(properties.safeHealthyReconcileInterval()));
    clearProviderError(control);
    controlRepository.save(control);

    ChannelConnection connection = requireConnection(connectionId);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection.setLastSyncAt(now);
    connectionRepository.save(connection);

    senderIdentityRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId).stream()
        .filter(sender -> sender.getExternalId() != null)
        .forEach(sender -> sender.setStatus(SenderIdentityStatus.ACTIVE));
    senderIdentityRepository.saveAll(
        senderIdentityRepository.findByConnectionIdOrderByCreatedAtAsc(connectionId));

    updateOperation(connectionId, ProvisioningStep.HEALTH_VALIDATED, OperationState.SUCCEEDED, null);
  }

  @Transactional
  public void markFailure(Long connectionId, MetaGraphException failure) {
    MetaWhatsAppConnectionControl control = requireControl(connectionId);
    control.setLastProviderHttpStatus(failure.httpStatus() == 0 ? null : failure.httpStatus());
    control.setLastProviderErrorType(failure.metaType());
    control.setLastProviderErrorCode(failure.metaCode());
    control.setLastProviderErrorSubcode(failure.metaSubcode());
    control.setLastProviderFbtraceId(failure.fbtraceId());
    control.setLastSafeErrorCode(failure.safeCode());

    switch (failure.safeCategory()) {
      case AUTH -> {
        control.setAuthState(AuthState.REVOKED);
        control.setProductState(ProductState.REAUTH_REQUIRED);
      }
      case PERMISSION -> {
        control.setAuthState(AuthState.MISSING_PERMISSION);
        control.setProductState(ProductState.REAUTH_REQUIRED);
      }
      case RATE_LIMIT, TRANSIENT, PROVIDER_ERROR -> control.setProductState(ProductState.DEGRADED);
      case ASSET, INVALID_REQUEST -> control.setProductState(ProductState.ACTION_REQUIRED);
    }

    control.setNextReconcileAt(Instant.now().plus(properties.safeDegradedReconcileInterval()));
    controlRepository.save(control);

    ChannelConnection connection = requireConnection(connectionId);
    connection.setStatus(genericStatus(control.getProductState()));
    connectionRepository.save(connection);

    updateOperation(
        connectionId,
        currentFailureStep(control),
        failure.retryable() ? OperationState.FAILED_RETRYABLE : OperationState.FAILED_PERMANENT,
        failure.safeCode());
  }

  @Transactional
  public void markAssetFailure(Long connectionId, String safeCode) {
    MetaWhatsAppConnectionControl control = requireControl(connectionId);
    control.setProductState(ProductState.ACTION_REQUIRED);
    control.setLastSafeErrorCode(safeCode);
    control.setNextReconcileAt(Instant.now().plus(properties.safeDegradedReconcileInterval()));
    controlRepository.save(control);
    ChannelConnection connection = requireConnection(connectionId);
    connection.setStatus(ChannelConnectionStatus.ERROR);
    connectionRepository.save(connection);
    updateOperation(
        connectionId,
        ProvisioningStep.ASSETS_VALIDATED,
        OperationState.FAILED_PERMANENT,
        safeCode);
  }

  @Transactional
  public void markDisconnected(Long connectionId) {
    controlRepository
        .findById(connectionId)
        .ifPresent(
            control -> {
              control.setProductState(ProductState.DISCONNECTED);
              control.setAuthState(AuthState.UNKNOWN);
              control.setNextReconcileAt(null);
              controlRepository.save(control);
            });
  }

  @Transactional(readOnly = true)
  public MetaWhatsAppConnectionControl requireControl(Long companyId, Long connectionId) {
    return controlRepository
        .findByConnectionIdAndCompanyId(connectionId, companyId)
        .orElseThrow(() -> new NotFoundException("Meta WhatsApp connection not found"));
  }

  @Transactional(readOnly = true)
  public StoredMetaToken readToken(Long connectionId) {
    try {
      return objectMapper.readValue(
          credentialService.read(connectionId, CredentialType.OAUTH_TOKEN_SET),
          StoredMetaToken.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Meta credential is invalid", exception);
    }
  }

  @Transactional(readOnly = true)
  public String readPin(Long connectionId) {
    if (!properties.isRegisterPhoneNumber()) {
      return null;
    }
    try {
      JsonNode root =
          objectMapper.readTree(
              credentialService.read(connectionId, CredentialType.PROVIDER_SECRET));
      String pin = root.path("twoStepPin").asText(null);
      if (pin == null || !pin.matches("\\d{6}")) {
        throw new IllegalStateException("Stored Meta registration secret is invalid");
      }
      return pin;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Meta registration secret is invalid", exception);
    }
  }

  private Optional<ChannelConnection> resolveConnection(
      Long companyId, Long expectedConnectionId, String wabaId) {
    if (expectedConnectionId != null) {
      return Optional.of(
          connectionRepository
              .findByIdAndCompanyId(expectedConnectionId, companyId)
              .orElseThrow(() -> new NotFoundException("Meta WhatsApp connection not found")));
    }
    return connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
        companyId, ProviderType.META_WHATSAPP, wabaId);
  }

  private String stablePin(Long connectionId) {
    if (!properties.isRegisterPhoneNumber()) {
      return null;
    }
    try {
      return readPin(connectionId);
    } catch (NotFoundException exception) {
      return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
  }

  private void updateOperation(
      Long connectionId, ProvisioningStep step, OperationState state, String errorCode) {
    operationRepository
        .findFirstByConnectionIdOrderByUpdatedAtDesc(connectionId)
        .ifPresent(
            operation -> {
              operation.setStep(step);
              operation.setState(state);
              operation.setLastSafeErrorCode(errorCode);
              operation.setAttemptCount(operation.getAttemptCount() + 1);
              operation.setNextAttemptAt(
                  state == OperationState.FAILED_RETRYABLE
                      ? Instant.now().plus(properties.safeDegradedReconcileInterval())
                      : null);
              operation.setCompletedAt(state == OperationState.SUCCEEDED ? Instant.now() : null);
              operationRepository.save(operation);
            });
  }

  private ProvisioningStep currentFailureStep(MetaWhatsAppConnectionControl control) {
    if (control.getRegistrationState() != RegistrationState.REGISTERED) {
      return ProvisioningStep.PHONE_REGISTERED;
    }
    if (control.getSubscriptionState() != SubscriptionState.SUBSCRIBED) {
      return ProvisioningStep.APP_SUBSCRIBED;
    }
    return ProvisioningStep.HEALTH_VALIDATED;
  }

  private ChannelConnection requireConnection(Long connectionId) {
    return connectionRepository
        .findById(connectionId)
        .orElseThrow(() -> new NotFoundException("Channel connection not found"));
  }

  private MetaWhatsAppConnectionControl requireControl(Long connectionId) {
    return controlRepository
        .findById(connectionId)
        .orElseThrow(() -> new NotFoundException("Meta WhatsApp connection not found"));
  }

  private void clearProviderError(MetaWhatsAppConnectionControl control) {
    control.setLastProviderHttpStatus(null);
    control.setLastProviderErrorType(null);
    control.setLastProviderErrorCode(null);
    control.setLastProviderErrorSubcode(null);
    control.setLastProviderFbtraceId(null);
    control.setLastSafeErrorCode(null);
  }

  private ChannelConnectionStatus genericStatus(ProductState state) {
    return switch (state) {
      case ACTIVE, DEGRADED -> ChannelConnectionStatus.ACTIVE;
      case CONNECTING, VALIDATING -> ChannelConnectionStatus.PENDING;
      case ACTION_REQUIRED, REAUTH_REQUIRED, ERROR -> ChannelConnectionStatus.ERROR;
      case DISCONNECTED -> ChannelConnectionStatus.DISCONNECTED;
    };
  }

  private String displayValue(PhoneNumber phone) {
    return phone.displayPhoneNumber() == null || phone.displayPhoneNumber().isBlank()
        ? phone.id()
        : phone.displayPhoneNumber().trim();
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta credential", exception);
    }
  }

  public record PendingProvisioning(
      Long connectionId,
      Long senderIdentityId,
      String pin,
      String wabaId,
      String phoneNumberId,
      String displayPhoneNumber,
      String verifiedName,
      String qualityRating) {}

  public record StoredMetaToken(
      String accessToken,
      String wabaId,
      String phoneNumberId,
      String graphApiVersion,
      String whatsappAccountId,
      String messagingAccountId) {}

  private record StoredMetaSecret(String twoStepPin) {}
}
