package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAdvancedCampaignRequest;
import com.messaging.ads.service.AdActivityLogService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsAdvancedCampaignServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsCampaignSyncService campaignSyncService;
  @Mock private GoogleAdsCampaignPersistenceService campaignPersistenceService;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsAdvancedCampaignService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAdvancedCampaignService(
            contextService,
            apiClient,
            campaignSyncService,
            campaignPersistenceService,
            activityLogService);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setCurrency("INR");
    account.setManaged(true);
    account.setAccessible(true);

    context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsShoppingCoreAtomicallyAndPaused() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "-1"))
        .thenReturn("customers/1234567890/campaignBudgets/-1");
    when(apiClient.customerResourceName("1234567890", "campaigns", "-2"))
        .thenReturn("customers/1234567890/campaigns/-2");
    when(apiClient.customerResourceName("1234567890", "adGroups", "-3"))
        .thenReturn("customers/1234567890/adGroups/-3");
    when(apiClient.mutateAll(eq("access-token"), eq("1234567890"), eq(null), any()))
        .thenReturn(objectMapper.readTree("{\"mutateOperationResponses\":[]}"));
    when(apiClient.firstBulkResourceName(any(), eq("campaignResult")))
        .thenReturn("customers/1234567890/campaigns/888");

    var campaignRow =
        objectMapper.readTree(
            """
            {"campaign":{"id":"888","name":"Shopping sales","status":"PAUSED","advertisingChannelType":"SHOPPING"}}
            """);
    when(campaignSyncService.campaign(context, "888")).thenReturn(campaignRow);

    AdCampaign persisted = new AdCampaign();
    persisted.setId(800L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalCampaignId("888");
    persisted.setName("Shopping sales");
    persisted.setObjective("SHOPPING");
    persisted.setStatus(AdEntityStatus.PAUSED);
    persisted.setAccessible(true);
    when(campaignPersistenceService.persistOne(10L, 20L, campaignRow))
        .thenReturn(persisted);

    service.create(
        10L,
        20L,
        new CreateGoogleAdvancedCampaignRequest(
            "Shopping sales",
            "SHOPPING",
            20_000L,
            null,
            "MANUAL_CPC",
            null,
            null,
            "123456",
            1,
            false,
            false,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)),
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());

    assertThat(operations.getValue()).hasSize(5);

    Map<String, Object> campaignOperation =
        (Map<String, Object>) operations.getValue().get(1);
    Map<String, Object> campaignCreate =
        (Map<String, Object>)
            ((Map<String, Object>) campaignOperation.get("campaignOperation"))
                .get("create");
    assertThat(campaignCreate.get("status")).isEqualTo("PAUSED");
    assertThat(campaignCreate.get("advertisingChannelType")).isEqualTo("SHOPPING");

    Map<String, Object> adGroupOperation =
        (Map<String, Object>) operations.getValue().get(2);
    Map<String, Object> adGroupCreate =
        (Map<String, Object>)
            ((Map<String, Object>) adGroupOperation.get("adGroupOperation"))
                .get("create");
    assertThat(adGroupCreate.get("status")).isEqualTo("PAUSED");
    assertThat(adGroupCreate.get("type")).isEqualTo("SHOPPING_PRODUCT_ADS");

    Map<String, Object> adOperation =
        (Map<String, Object>) operations.getValue().get(3);
    Map<String, Object> adCreate =
        (Map<String, Object>)
            ((Map<String, Object>) adOperation.get("adGroupAdOperation"))
                .get("create");
    assertThat(adCreate.get("status")).isEqualTo("PAUSED");

    Map<String, Object> criterionOperation =
        (Map<String, Object>) operations.getValue().get(4);
    Map<String, Object> criterionCreate =
        (Map<String, Object>)
            ((Map<String, Object>) criterionOperation.get("adGroupCriterionOperation"))
                .get("create");
    assertThat(criterionCreate.get("status")).isEqualTo("PAUSED");
    assertThat(criterionCreate.get("listingGroup"))
        .isEqualTo(Map.of("type", "UNIT"));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsDemandGenCampaignWithCustomPeriodTotalBudget() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "campaignBudgets", "-1"))
        .thenReturn("customers/1234567890/campaignBudgets/-1");
    when(apiClient.customerResourceName("1234567890", "campaigns", "-2"))
        .thenReturn("customers/1234567890/campaigns/-2");
    when(apiClient.mutateAll(
            eq("access-token"), eq("1234567890"), eq(null), any()))
        .thenReturn(objectMapper.readTree("{\"mutateOperationResponses\":[]}"));
    when(apiClient.firstBulkResourceName(any(), eq("campaignResult")))
        .thenReturn("customers/1234567890/campaigns/777");

    var campaignRow =
        objectMapper.readTree(
            """
            {"campaign":{"id":"777","name":"Demand Gen total","status":"PAUSED","advertisingChannelType":"DEMAND_GEN"}}
            """);
    when(campaignSyncService.campaign(context, "777")).thenReturn(campaignRow);

    AdCampaign persisted = new AdCampaign();
    persisted.setId(700L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalCampaignId("777");
    persisted.setName("Demand Gen total");
    persisted.setObjective("DEMAND_GEN");
    persisted.setStatus(AdEntityStatus.PAUSED);
    persisted.setAccessible(true);
    when(campaignPersistenceService.persistOne(10L, 20L, campaignRow))
        .thenReturn(persisted);

    service.create(
        10L,
        20L,
        new CreateGoogleAdvancedCampaignRequest(
            "Demand Gen total",
            "DEMAND_GEN",
            null,
            100_000L,
            "MAXIMIZE_CONVERSIONS",
            null,
            (BigDecimal) null,
            null,
            null,
            null,
            false,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)),
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());

    Map<String, Object> firstOperation =
        (Map<String, Object>) operations.getValue().getFirst();
    Map<String, Object> budgetOperation =
        (Map<String, Object>) firstOperation.get("campaignBudgetOperation");
    Map<String, Object> budget =
        (Map<String, Object>) budgetOperation.get("create");

    assertThat(budget.get("period")).isEqualTo("CUSTOM_PERIOD");
    assertThat(budget).containsKey("totalAmountMicros");
    assertThat(budget).doesNotContainKey("amountMicros");
    assertThat(budget.get("explicitlyShared")).isEqualTo(false);
  }
}
