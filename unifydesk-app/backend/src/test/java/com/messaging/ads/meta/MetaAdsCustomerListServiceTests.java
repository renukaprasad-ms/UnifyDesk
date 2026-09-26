package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaCustomerListMembersRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsCustomerListServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MetaAdsCustomerListService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsCustomerListService(
            accountRepository,
            audienceRepository,
            connectionRepository,
            tokenService,
            apiClient,
            activityLogService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void normalizesAndHashesEmailBeforeProviderUpload() throws Exception {
    stubContext();

    when(apiClient.customAudienceUsers(
            eq("token"),
            eq("meta-audience"),
            eq(false),
            any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {"num_received":1,"num_invalid_entries":0}
                """));

    service.mutateMembers(
        10L,
        40L,
        new MetaCustomerListMembersRequest(
            List.of("EMAIL"),
            List.of(List.of(" Test@Example.COM ")),
            MetaCustomerListMembersRequest.Operation.ADD,
            false,
            true),
        99L);

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    verify(apiClient)
        .customAudienceUsers(
            eq("token"),
            eq("meta-audience"),
            eq(false),
            payload.capture());

    Map<String, Object> body = payload.getValue();
    Map<String, Object> providerPayload =
        (Map<String, Object>) body.get("payload");
    List<List<String>> data =
        (List<List<String>>) providerPayload.get("data");

    assertThat(providerPayload.get("schema")).isEqualTo(List.of("EMAIL"));
    assertThat(providerPayload.get("is_raw")).isEqualTo(false);
    assertThat(data).hasSize(1);
    assertThat(data.getFirst().getFirst())
        .isEqualTo(
            "973dfe463ec85785f5f95af5ba3906eedb2d931c24e69824a89ea65dba4e813b");
    assertThat(data.getFirst().getFirst()).doesNotContain("example.com");
  }

  @Test
  @SuppressWarnings("unchecked")
  void preservesExternalIdAndSupportsRemove() throws Exception {
    stubContext();

    when(apiClient.customAudienceUsers(
            eq("token"),
            eq("meta-audience"),
            eq(true),
            any()))
        .thenReturn(objectMapper.readTree("{\"num_received\":1}"));

    var result =
        service.mutateMembers(
            10L,
            40L,
            new MetaCustomerListMembersRequest(
                List.of("EXTERN_ID"),
                List.of(List.of("crm-user-42")),
                MetaCustomerListMembersRequest.Operation.REMOVE,
                false,
                true),
            99L);

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    verify(apiClient)
        .customAudienceUsers(
            eq("token"),
            eq("meta-audience"),
            eq(true),
            payload.capture());

    Map<String, Object> providerPayload =
        (Map<String, Object>) payload.getValue().get("payload");
    List<List<String>> data =
        (List<List<String>>) providerPayload.get("data");

    assertThat(data.getFirst().getFirst()).isEqualTo("crm-user-42");
    assertThat(result.operation()).isEqualTo(MetaCustomerListMembersRequest.Operation.REMOVE);
    assertThat(result.providerReceivedRows()).isEqualTo(1);
  }

  @Test
  void rejectsPreHashedValuesThatAreNotSha256() {
    stubContext();

    assertThatThrownBy(
            () ->
                service.mutateMembers(
                    10L,
                    40L,
                    new MetaCustomerListMembersRequest(
                        List.of("EMAIL"),
                        List.of(List.of("not-a-hash")),
                        MetaCustomerListMembersRequest.Operation.ADD,
                        true,
                        true),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("SHA-256");
  }

  private void stubContext() {
    AdAudience audience = new AdAudience();
    audience.setId(40L);
    audience.setCompanyId(10L);
    audience.setAdAccountId(20L);
    audience.setProvider(AdProvider.META_ADS);
    audience.setExternalAudienceId("meta-audience");
    audience.setSubtype("CUSTOM");
    audience.setAccessible(true);
    when(audienceRepository.findByIdAndCompanyId(40L, 10L))
        .thenReturn(Optional.of(audience));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setManaged(true);
    account.setAccessible(true);
    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);
    when(connectionRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(connection));

    when(tokenService.read(30L))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));
  }
}
