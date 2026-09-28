package com.messaging.ads.google;

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
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdSetRepository;
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
class GoogleAdsVisualAudienceServiceTests {

  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsVisualAudienceService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsVisualAudienceService(
            audienceRepository,
            adSetRepository,
            contextService,
            apiClient,
            objectMapper);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setManaged(true);
    account.setAccessible(true);

    context =
        new GoogleAdsAccountContextService.Context(
            account,
            new AdProviderConnection(),
            "access-token",
            null,
            false,
            false);
  }

  @Test
  void rejectsGoogleListThatIsNotDisplayEligible() {
    AdAudience audience = googleAudience(101L, 20L, "501", "Search-only");
    audience.setDeliveryStatusJson(
        "{\"eligibleForDisplay\":false,\"accountStatus\":\"ENABLED\"}");
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(audience));

    assertThatThrownBy(
            () ->
                service.validateUserLists(
                    10L, 20L, List.of(101L), List.of()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not eligible for Display");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void appliesPositiveAndNegativeDisplayUserListCriteria() throws Exception {
    AdAudience included = googleAudience(101L, 20L, "501", "Customers");
    AdAudience excluded = googleAudience(102L, 20L, "502", "Converted");
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(included));
    when(audienceRepository.findByIdAndCompanyId(102L, 10L))
        .thenReturn(Optional.of(excluded));

    AdSet adGroup = new AdSet();
    adGroup.setId(30L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("700");
    adGroup.setAccessible(true);
    when(adSetRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(adGroup));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "adGroups", "700"))
        .thenReturn("customers/1234567890/adGroups/700");
    when(apiClient.customerResourceName("1234567890", "userLists", "501"))
        .thenReturn("customers/1234567890/userLists/501");
    when(apiClient.customerResourceName("1234567890", "userLists", "502"))
        .thenReturn("customers/1234567890/userLists/502");
    when(apiClient.searchRows(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            any(String.class)))
        .thenReturn(List.of());
    when(apiClient.mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupCriteria"),
            any()))
        .thenReturn(objectMapper.readTree("{\"results\":[]}"));

    var result =
        service.applyDisplayUserLists(
            10L, 30L, List.of(101L), List.of(102L));

    assertThat(result.createdCount()).isEqualTo(2);
    assertThat(result.reusedCount()).isZero();

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupCriteria"),
            operations.capture());

    assertThat(operations.getValue()).hasSize(2);
    Map<String, Object> includeCreate =
        (Map<String, Object>)
            ((Map<String, Object>) operations.getValue().get(0)).get("create");
    Map<String, Object> excludeCreate =
        (Map<String, Object>)
            ((Map<String, Object>) operations.getValue().get(1)).get("create");

    assertThat(includeCreate.get("negative")).isEqualTo(false);
    assertThat(excludeCreate.get("negative")).isEqualTo(true);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void fingerprintsReusableAudienceComposition() throws Exception {
    AdAudience included = googleAudience(101L, 20L, "501", "Customers");
    AdAudience excluded = googleAudience(102L, 20L, "502", "Converted");
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(included));
    when(audienceRepository.findByIdAndCompanyId(102L, 10L))
        .thenReturn(Optional.of(excluded));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.searchRows(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            any(String.class)))
        .thenReturn(List.of());
    when(apiClient.customerResourceName("1234567890", "userLists", "501"))
        .thenReturn("customers/1234567890/userLists/501");
    when(apiClient.customerResourceName("1234567890", "userLists", "502"))
        .thenReturn("customers/1234567890/userLists/502");
    when(apiClient.mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("audiences"),
            any()))
        .thenReturn(
            objectMapper.readTree(
                "{\"results\":[{\"resourceName\":\"customers/1234567890/audiences/900\"}]}"));
    when(apiClient.firstMutatedResourceName(any()))
        .thenReturn("customers/1234567890/audiences/900");

    String resource =
        service.ensureCustomerAudience(
            10L,
            20L,
            "Campaign Demand Gen Audience",
            List.of(101L),
            List.of(102L));

    assertThat(resource)
        .isEqualTo("customers/1234567890/audiences/900");

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("audiences"),
            operations.capture());

    Map<String, Object> create =
        (Map<String, Object>)
            ((Map<String, Object>) operations.getValue().getFirst()).get("create");
    assertThat(String.valueOf(create.get("name")))
        .startsWith("Campaign Demand Gen Audience [")
        .endsWith("]");
    assertThat(create).containsKey("exclusionDimension");
  }

  private AdAudience googleAudience(
      Long id,
      Long accountId,
      String externalId,
      String name) {
    AdAudience audience = new AdAudience();
    audience.setId(id);
    audience.setCompanyId(10L);
    audience.setAdAccountId(accountId);
    audience.setProvider(AdProvider.GOOGLE_ADS);
    audience.setExternalAudienceId(externalId);
    audience.setName(name);
    audience.setAccessible(true);
    audience.setDeliveryStatusJson(
        "{\"eligibleForDisplay\":true,\"accountStatus\":\"ENABLED\"}");
    return audience;
  }
}
