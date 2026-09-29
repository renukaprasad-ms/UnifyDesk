package com.messaging.channel.meta;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.DisconnectDataPolicy;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.meta.MetaGraphClient.GranularScope;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenInspection;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.MetaWhatsAppPersistenceService.PendingProvisioning;
import com.messaging.channel.meta.MetaWhatsAppPersistenceService.StoredMetaToken;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.ConsumedSignupState;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.SignupMode;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.SignupSession;
import com.messaging.channel.meta.control.MetaGraphException;
import com.messaging.channel.meta.control.MetaGraphException.SafeCategory;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation.OperationType;
import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionHealthResponse;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse.NextAction;
import com.messaging.channel.meta.dto.MetaWhatsAppSignupConfigResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppConnectionService {

  private static final Set<String> REQUIRED_SCOPES =
      Set.of("whatsapp_business_management", "whatsapp_business_messaging");

  private final MetaWhatsAppProperties properties;
  private final MetaWhatsAppSignupStateService stateService;
  private final MetaGraphClient graphClient;
  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final MetaWhatsAppConnectionControlRepository controlRepository;
  private final ChannelEntitlementService entitlementService;
  private final ChannelConnectionService channelConnectionService;
  private final MetaWhatsAppPersistenceService persistenceService;

  public MetaWhatsAppSignupConfigResponse signupSession(Long companyId, Long userId) {
    requireConfigured();
    SignupSession session = stateService.create(companyId, userId);
    return signupResponse(session);
  }

  public MetaWhatsAppSignupConfigResponse reauthorizeSession(Long companyId, Long userId) {
    requireConfigured();
    MetaWhatsAppConnectionControl control = currentControl(companyId);
    SignupSession session =
        stateService.createReauthorize(
            companyId, userId, control.getConnectionId(), control.getSelectedPhoneNumberId());
    return signupResponse(session);
  }

  public MetaWhatsAppConnectionResponse complete(
      Long companyId, Long userId, CompleteMetaWhatsAppSignupRequest request) {
    requireConfigured();
    ConsumedSignupState signupState = stateService.consume(request.state(), companyId, userId);

    TokenResponse token = graphClient.exchangeCode(request.code());
    if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("WHATSAPP_PROVIDER_AUTH_FAILED: Meta did not return a token");
    }

    TokenInspection inspection = graphClient.inspectToken(token.accessToken());
    validateTokenInspection(inspection);

    String wabaId = resolveWabaId(request.wabaId(), inspection);
    WabaDetails waba = graphClient.getWaba(wabaId, token.accessToken());
    if (waba == null || waba.id() == null || !wabaId.equals(waba.id())) {
      throw new BadRequestException("WHATSAPP_ASSET_NOT_GRANTED: selected account was not validated");
    }

    List<PhoneNumber> phones = graphClient.getPhoneNumbers(wabaId, token.accessToken());
    PhoneNumber phone = resolvePhoneNumber(request.phoneNumberId(), phones);
    validateReauthorizationBinding(companyId, signupState, wabaId, phone.id());
    rejectForeignPhone(companyId, phone.id());

    ChannelConnection existing = existingConnection(companyId, signupState, wabaId);
    if (existing == null) {
      entitlementService.assertCanCreateConnection(companyId, ChannelType.WHATSAPP);
      entitlementService.assertCanCreateSender(companyId, SenderIdentityType.PHONE_NUMBER);
    } else if (senderIdentityRepository
        .findByConnectionIdAndIdentityTypeAndExternalId(
            existing.getId(), SenderIdentityType.PHONE_NUMBER, phone.id())
        .isEmpty()) {
      entitlementService.assertCanCreateSender(companyId, SenderIdentityType.PHONE_NUMBER);
    }

    Instant tokenExpiresAt = resolveTokenExpiry(token, inspection);
    OperationType operationType =
        signupState.mode() == SignupMode.REAUTHORIZE
            ? OperationType.REAUTHORIZE
            : OperationType.CONNECT;

    PendingProvisioning pending =
        persistenceService.persistPending(
            companyId,
            userId,
            signupState.expectedConnectionId(),
            wabaId,
            request.whatsappAccountId(),
            request.messagingAccountId(),
            waba,
            phone,
            token,
            tokenExpiresAt,
            operationType);
    channelConnectionService.cancelPendingPurge(companyId, pending.connectionId());

    provision(companyId, pending.connectionId());
    return connectionResponse(companyId, pending.connectionId());
  }

  public MetaWhatsAppConnectionHealthResponse getConnection(Long companyId) {
    MetaWhatsAppConnectionControl control = currentControl(companyId);
    return healthResponse(companyId, control.getConnectionId());
  }

  public MetaWhatsAppConnectionHealthResponse reconcile(Long companyId) {
    MetaWhatsAppConnectionControl control = currentControl(companyId);
    if (control.getLastValidatedAt() != null
        && control
            .getLastValidatedAt()
            .plus(properties.safeManualReconcileMinInterval())
            .isAfter(Instant.now())
        && control.getProductState() == ProductState.ACTIVE) {
      return healthResponse(companyId, control.getConnectionId());
    }
    provision(companyId, control.getConnectionId());
    return healthResponse(companyId, control.getConnectionId());
  }

  public void reconcileDue(Long companyId, Long connectionId) {
    try {
      persistenceService.requireControl(companyId, connectionId);
      provision(companyId, connectionId);
    } catch (NotFoundException ignored) {
      // Connection was removed or disconnected after the scheduler selected it.
    }
  }

  public MetaWhatsAppConnectionHealthResponse disconnect(
      Long companyId, Long userId, DisconnectDataPolicy dataPolicy) {
    MetaWhatsAppConnectionControl control = currentControl(companyId);
    channelConnectionService.disconnect(
        companyId,
        control.getConnectionId(),
        userId,
        dataPolicy == null ? DisconnectDataPolicy.KEEP_7_DAYS : dataPolicy);
    return healthResponse(companyId, control.getConnectionId());
  }

  private void provision(Long companyId, Long connectionId) {
    MetaWhatsAppConnectionControl control =
        persistenceService.requireControl(companyId, connectionId);
    if (control.getProductState() == ProductState.DISCONNECTED) {
      return;
    }

    StoredMetaToken stored = persistenceService.readToken(connectionId);
    try {
      if (control.getRegistrationState() != RegistrationState.REGISTERED) {
        graphClient.registerPhoneNumber(
            control.getSelectedPhoneNumberId(),
            stored.accessToken(),
            persistenceService.readPin(connectionId));
        persistenceService.markRegistrationSucceeded(connectionId);
      }

      control = persistenceService.requireControl(companyId, connectionId);
      if (control.getSubscriptionState() != SubscriptionState.SUBSCRIBED) {
        graphClient.subscribeApp(control.getLegacyWabaId(), stored.accessToken());
        persistenceService.markSubscriptionSucceeded(connectionId);
      }

      reconcileProviderState(companyId, connectionId, stored);
    } catch (MetaGraphException failure) {
      persistenceService.markFailure(connectionId, failure);
    }
  }

  private void reconcileProviderState(
      Long companyId, Long connectionId, StoredMetaToken stored) {
    TokenInspection inspection = graphClient.inspectToken(stored.accessToken());
    try {
      validateTokenInspection(inspection);
    } catch (BadRequestException exception) {
      persistenceService.markFailure(
          connectionId,
          new MetaGraphException(
              "reconcile_token",
              403,
              null,
              null,
              null,
              null,
              SafeCategory.PERMISSION,
              false));
      return;
    }

    WabaDetails waba = graphClient.getWaba(stored.wabaId(), stored.accessToken());
    if (waba == null || !stored.wabaId().equals(waba.id())) {
      persistenceService.markAssetFailure(connectionId, "WHATSAPP_ASSET_NOT_GRANTED");
      return;
    }

    boolean phoneStillGranted =
        graphClient.getPhoneNumbers(stored.wabaId(), stored.accessToken()).stream()
            .anyMatch(phone -> stored.phoneNumberId().equals(phone.id()));
    if (!phoneStillGranted) {
      persistenceService.markAssetFailure(connectionId, "WHATSAPP_ASSET_NOT_GRANTED");
      return;
    }

    if (!graphClient.isAppSubscribed(stored.wabaId(), stored.accessToken())) {
      graphClient.subscribeApp(stored.wabaId(), stored.accessToken());
      persistenceService.markSubscriptionSucceeded(connectionId);
    }

    persistenceService.markHealthy(connectionId);
  }

  private MetaWhatsAppSignupConfigResponse signupResponse(SignupSession session) {
    return new MetaWhatsAppSignupConfigResponse(
        properties.getAppId(),
        properties.getConfigurationId(),
        properties.getGraphApiVersion(),
        "4",
        session.state(),
        session.expiresAt(),
        properties.isCoexistenceEnabled());
  }

  private MetaWhatsAppConnectionResponse connectionResponse(Long companyId, Long connectionId) {
    MetaWhatsAppConnectionControl control =
        persistenceService.requireControl(companyId, connectionId);
    SenderIdentity sender = sender(connectionId, control.getSelectedPhoneNumberId());
    return new MetaWhatsAppConnectionResponse(
        connectionId.toString(),
        control.getProductState(),
        control.getLegacyWabaId(),
        control.getWhatsappAccountId(),
        control.getMessagingAccountId(),
        new MetaWhatsAppConnectionResponse.Phone(
            sender.getId().toString(),
            control.getSelectedPhoneNumberId(),
            sender.getValue(),
            null,
            null),
        nextAction(control));
  }

  private MetaWhatsAppConnectionHealthResponse healthResponse(
      Long companyId, Long connectionId) {
    MetaWhatsAppConnectionControl control =
        persistenceService.requireControl(companyId, connectionId);
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Meta WhatsApp connection not found"));
    SenderIdentity sender = sender(connectionId, control.getSelectedPhoneNumberId());
    return new MetaWhatsAppConnectionHealthResponse(
        connectionId.toString(),
        connection.getStatus(),
        control.getProductState(),
        control.getAuthState(),
        control.getRegistrationState(),
        control.getSubscriptionState(),
        control.getCoexistenceState(),
        control.getLastValidatedAt(),
        new MetaWhatsAppConnectionResponse.Phone(
            sender.getId().toString(),
            control.getSelectedPhoneNumberId(),
            sender.getValue(),
            null,
            null),
        control.getLastSafeErrorCode(),
        nextAction(control));
  }

  private void validateTokenInspection(TokenInspection inspection) {
    if (inspection == null || !inspection.valid()) {
      throw new BadRequestException("WHATSAPP_PROVIDER_AUTH_FAILED: Meta token is invalid");
    }
    if (!inspection.scopes().containsAll(REQUIRED_SCOPES)) {
      throw new BadRequestException(
          "WHATSAPP_PERMISSION_MISSING: required WhatsApp permissions were not granted");
    }
  }

  private String resolveWabaId(String requestedWabaId, TokenInspection inspection) {
    Set<String> granted = grantedTargetIds(inspection.granularScopes());
    if (requestedWabaId != null && !requestedWabaId.isBlank()) {
      String requested = requestedWabaId.trim();
      if (!granted.isEmpty() && !granted.contains(requested)) {
        throw new BadRequestException(
            "WHATSAPP_ASSET_NOT_GRANTED: selected WhatsApp account was not granted");
      }
      return requested;
    }
    if (granted.isEmpty()) {
      throw new BadRequestException(
          "WHATSAPP_ASSET_NOT_GRANTED: no WhatsApp account was granted");
    }
    if (granted.size() != 1) {
      throw new BadRequestException(
          "WHATSAPP_ASSET_NOT_GRANTED: multiple WhatsApp accounts were granted; select one");
    }
    return granted.iterator().next();
  }

  private Set<String> grantedTargetIds(List<GranularScope> scopes) {
    Set<String> granted = new LinkedHashSet<>();
    if (scopes == null) {
      return granted;
    }
    scopes.stream()
        .filter(scope -> scope != null && REQUIRED_SCOPES.contains(scope.scope()))
        .filter(scope -> scope.targetIds() != null)
        .flatMap(scope -> scope.targetIds().stream())
        .filter(value -> value != null && !value.isBlank())
        .forEach(granted::add);
    return granted;
  }

  private PhoneNumber resolvePhoneNumber(String requestedPhoneNumberId, List<PhoneNumber> phones) {
    if (requestedPhoneNumberId != null && !requestedPhoneNumberId.isBlank()) {
      String requested = requestedPhoneNumberId.trim();
      return phones.stream()
          .filter(phone -> requested.equals(phone.id()))
          .findFirst()
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "WHATSAPP_ASSET_NOT_GRANTED: selected phone is not granted"));
    }
    if (phones.isEmpty()) {
      throw new BadRequestException("WHATSAPP_ASSET_NOT_GRANTED: account has no phone number");
    }
    if (phones.size() != 1) {
      throw new BadRequestException(
          "WHATSAPP_ASSET_NOT_GRANTED: account has multiple phones; select one");
    }
    return phones.getFirst();
  }

  private void validateReauthorizationBinding(
      Long companyId, ConsumedSignupState signupState, String wabaId, String phoneNumberId) {
    if (signupState.mode() != SignupMode.REAUTHORIZE) {
      return;
    }
    if (signupState.expectedConnectionId() == null || signupState.expectedPhoneNumberId() == null) {
      throw new BadRequestException("WHATSAPP_REAUTH_REQUIRED: reauthorization binding is invalid");
    }
    if (!signupState.expectedPhoneNumberId().equals(phoneNumberId)) {
      throw new ConflictException(
          "WHATSAPP_ASSET_NOT_GRANTED: reauthorization cannot transfer to another phone");
    }
    MetaWhatsAppConnectionControl expected =
        persistenceService.requireControl(companyId, signupState.expectedConnectionId());
    if (!phoneNumberId.equals(expected.getSelectedPhoneNumberId())
        || (expected.getLegacyWabaId() != null && !wabaId.equals(expected.getLegacyWabaId()))) {
      throw new ConflictException(
          "WHATSAPP_ASSET_NOT_GRANTED: reauthorization must use the existing Meta asset");
    }
  }

  private void rejectForeignPhone(Long companyId, String phoneNumberId) {
    controlRepository
        .findFirstBySelectedPhoneNumberIdAndProductStateNot(
            phoneNumberId, ProductState.DISCONNECTED)
        .filter(control -> !companyId.equals(control.getCompanyId()))
        .ifPresent(
            control -> {
              throw new ConflictException(
                  "WHATSAPP_PHONE_OWNED_BY_OTHER_COMPANY: selected phone is already connected");
            });
  }

  private ChannelConnection existingConnection(
      Long companyId, ConsumedSignupState signupState, String wabaId) {
    if (signupState.expectedConnectionId() != null) {
      return connectionRepository
          .findByIdAndCompanyId(signupState.expectedConnectionId(), companyId)
          .orElseThrow(() -> new NotFoundException("Meta WhatsApp connection not found"));
    }
    return connectionRepository
        .findByCompanyIdAndProviderTypeAndExternalAccountId(
            companyId, ProviderType.META_WHATSAPP, wabaId)
        .orElse(null);
  }

  private Instant resolveTokenExpiry(TokenResponse token, TokenInspection inspection) {
    if (inspection.expiresAt() != null) {
      return inspection.expiresAt();
    }
    return token.expiresIn() == null || token.expiresIn() <= 0
        ? null
        : Instant.now().plusSeconds(token.expiresIn());
  }

  private MetaWhatsAppConnectionControl currentControl(Long companyId) {
    return controlRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
        .filter(control -> control.getProductState() != ProductState.DISCONNECTED)
        .findFirst()
        .orElseGet(
            () ->
                controlRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
                    .findFirst()
                    .orElseThrow(
                        () -> new NotFoundException("Meta WhatsApp connection not found")));
  }

  private SenderIdentity sender(Long connectionId, String phoneNumberId) {
    return senderIdentityRepository
        .findByConnectionIdAndIdentityTypeAndExternalId(
            connectionId, SenderIdentityType.PHONE_NUMBER, phoneNumberId)
        .orElseThrow(() -> new NotFoundException("Meta WhatsApp sender not found"));
  }

  private NextAction nextAction(MetaWhatsAppConnectionControl control) {
    return switch (control.getProductState()) {
      case ACTIVE -> NextAction.NONE;
      case CONNECTING, VALIDATING -> NextAction.WAIT;
      case DEGRADED -> NextAction.RETRY;
      case REAUTH_REQUIRED -> NextAction.REAUTHORIZE;
      case ACTION_REQUIRED -> NextAction.OPEN_META;
      case ERROR -> NextAction.RETRY;
      case DISCONNECTED -> NextAction.NONE;
    };
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "WHATSAPP_NOT_CONFIGURED: configure the Meta app before connecting WhatsApp");
    }
  }
}
