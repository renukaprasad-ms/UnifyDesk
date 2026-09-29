package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.meta.MetaGraphClient.GranularScope;
import com.messaging.channel.meta.MetaGraphClient.TokenInspection;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.ConsumedSignupState;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.SignupMode;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppWabaScopeAuthorizationTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long USER_ID = 202L;

  @Mock private MetaWhatsAppSignupStateService stateService;
  @Mock private MetaGraphClient graphClient;
  @Mock private ChannelConnectionRepository connectionRepository;
  @Mock private SenderIdentityRepository senderIdentityRepository;
  @Mock private MetaWhatsAppConnectionControlRepository controlRepository;
  @Mock private ChannelEntitlementService entitlementService;
  @Mock private ChannelConnectionService channelConnectionService;
  @Mock private MetaWhatsAppPersistenceService persistenceService;

  private MetaWhatsAppConnectionService service;

  @BeforeEach
  void setUp() {
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    properties.setEnabled(true);
    properties.setAppId("app-id");
    properties.setAppSecret("app-secret");
    properties.setConfigurationId("config-id");
    properties.setGraphApiVersion("v26.0");

    service =
        new MetaWhatsAppConnectionService(
            properties,
            stateService,
            graphClient,
            connectionRepository,
            senderIdentityRepository,
            controlRepository,
            entitlementService,
            channelConnectionService,
            persistenceService);
  }

  @Test
  void sameWabaGrantedByBothRequiredScopesIsEligible() {
    Set<String> granted =
        service.grantedTargetIds(
            List.of(
                scope("whatsapp_business_management", "waba-1"),
                scope("whatsapp_business_messaging", "waba-1")));

    assertThat(granted).containsExactly("waba-1");
  }

  @Test
  void managementOnlyGrantIsRejected() {
    Set<String> granted =
        service.grantedTargetIds(
            List.of(scope("whatsapp_business_management", "waba-management-only")));

    assertThat(granted).isEmpty();
  }

  @Test
  void messagingOnlyGrantIsRejected() {
    Set<String> granted =
        service.grantedTargetIds(
            List.of(scope("whatsapp_business_messaging", "waba-messaging-only")));

    assertThat(granted).isEmpty();
  }

  @Test
  void duplicateEntriesForOneScopeAreUnionedBeforeIntersection() {
    Set<String> granted =
        service.grantedTargetIds(
            List.of(
                scope("whatsapp_business_management", "waba-a"),
                scope("whatsapp_business_management", "waba-b"),
                scope("whatsapp_business_messaging", "waba-b")));

    assertThat(granted).containsExactly("waba-b");
  }

  @Test
  void onlyWabaPresentInEveryRequiredScopeSurvivesIntersection() {
    Set<String> granted =
        service.grantedTargetIds(
            List.of(
                new GranularScope(
                    "whatsapp_business_management", List.of("waba-a", "waba-common")),
                new GranularScope(
                    "whatsapp_business_messaging", List.of("waba-common", "waba-c"))));

    assertThat(granted).containsExactly("waba-common");
  }

  @Test
  void requestedWabaOutsideIntersectionIsRejectedBeforeProviderLookup() {
    CompleteMetaWhatsAppSignupRequest request =
        new CompleteMetaWhatsAppSignupRequest(
            "state", "code", "waba-management-only", "phone-1", null, null);

    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(
            new TokenInspection(
                true,
                Set.of("whatsapp_business_management", "whatsapp_business_messaging"),
                List.of(
                    new GranularScope(
                        "whatsapp_business_management",
                        List.of("waba-management-only", "waba-common")),
                    new GranularScope(
                        "whatsapp_business_messaging",
                        List.of("waba-common", "waba-messaging-only"))),
                null));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("WHATSAPP_ASSET_NOT_GRANTED");

    verify(graphClient, never()).getWaba(anyString(), anyString());
  }

  private GranularScope scope(String scope, String... targetIds) {
    return new GranularScope(scope, List.of(targetIds));
  }
}
