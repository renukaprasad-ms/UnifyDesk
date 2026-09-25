package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleCustomerMatchMember;
import com.messaging.ads.google.dto.GoogleCustomerMatchUploadRequest;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.ads.service.AdActivityLogService;
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
class GoogleDataManagerServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleDataManagerApiClient apiClient;
  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdConversionRepository conversionRepository;
  @Mock private AdActivityLogService activityLogService;

  private GoogleDataManagerService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleDataManagerService(
            contextService,
            apiClient,
            audienceRepository,
            conversionRepository,
            activityLogService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void hashesAndNormalizesCustomerMatchContactIdentifiersBeforeUpload()
      throws Exception {
    AdAudience audience = new AdAudience();
    audience.setId(55L);
    audience.setCompanyId(10L);
    audience.setAdAccountId(20L);
    audience.setProvider(AdProvider.GOOGLE_ADS);
    audience.setExternalAudienceId("12345");
    audience.setName("Reachlyo customers");
    audience.setAccessible(true);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1112223334");
    account.setName("Reachlyo Ads");
    account.setManaged(true);
    account.setAccessible(true);

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(AdProvider.GOOGLE_ADS);

    var context =
        new GoogleAdsAccountContextService.Context(
            account, connection, "access-token", "9998887776", false, true);

    when(audienceRepository.findByIdAndCompanyId(55L, 10L))
        .thenReturn(Optional.of(audience));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.ingestAudienceMembers(eq("access-token"), any()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"requestId\":\"request-1\",\"fieldWarnings\":[]}"));

    var request =
        new GoogleCustomerMatchUploadRequest(
            List.of(
                new GoogleCustomerMatchMember(
                    List.of("First.Last@Gmail.com"),
                    List.of("+919876543210"),
                    List.of(),
                    null)),
            true,
            false,
            true,
            false);

    var result = service.uploadAudienceMembers(10L, 55L, request, 77L);

    assertThat(result.requestIds()).containsExactly("request-1");
    assertThat(result.submittedCount()).isEqualTo(1);

    ArgumentCaptor<Map<String, Object>> bodyCaptor =
        ArgumentCaptor.forClass(Map.class);
    org.mockito.Mockito.verify(apiClient)
        .ingestAudienceMembers(eq("access-token"), bodyCaptor.capture());

    Map<String, Object> body = bodyCaptor.getValue();
    assertThat(body.get("encoding")).isEqualTo("HEX");
    assertThat(body.get("validateOnly")).isEqualTo(false);

    List<Map<String, Object>> members =
        (List<Map<String, Object>>) body.get("audienceMembers");
    Map<String, Object> composite =
        (Map<String, Object>) members.get(0).get("compositeData");
    Map<String, Object> userData =
        (Map<String, Object>) composite.get("userData");
    List<Map<String, String>> identifiers =
        (List<Map<String, String>>) userData.get("userIdentifiers");

    String serialized = identifiers.toString();
    assertThat(serialized)
        .doesNotContain("First.Last@Gmail.com")
        .doesNotContain("firstlast@gmail.com")
        .doesNotContain("+919876543210");
    assertThat(serialized).containsPattern("[0-9a-f]{64}");

    Map<String, Object> consent =
        (Map<String, Object>) body.get("consent");
    assertThat(consent.get("adUserData")).isEqualTo("CONSENT_GRANTED");
    assertThat(consent.get("adPersonalization")).isEqualTo("CONSENT_DENIED");
  }
}
