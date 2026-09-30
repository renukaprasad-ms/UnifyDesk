package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleLifecycleGoalUpsertRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxLifecycleRequest;
import com.messaging.ads.google.dto.GoogleUserListCustomerTypeRequest;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
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
class GoogleAdsPerformanceMaxLifecycleServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsPerformanceMaxLifecycleService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsPerformanceMaxLifecycleService(
            contextService,
            apiClient,
            audienceRepository,
            campaignRepository,
            activityLogService);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsNewCustomerAcquisitionAccountGoal() throws Exception {
    var context = context(10L, 20L, "1234567890");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(),
            List.of(
                objectMapper.readTree(
                    """
                    {
                      "goal": {
                        "resourceName": "customers/1234567890/goals/55",
                        "goalId": "55",
                        "goalType": "NEW_CUSTOMER_ACQUISITION",
                        "newCustomerAcquisitionGoalSettings": {
                          "valueSettings": {
                            "additionalValue": 50.0,
                            "additionalHighLifetimeValue": 100.0
                          }
                        }
                      }
                    }
                    """)));

    var result =
        service.upsertGoal(
            10L,
            20L,
            new GoogleLifecycleGoalUpsertRequest(
                "NEW_CUSTOMER_ACQUISITION", 50.0, 100.0, null),
            99L);

    assertThat(result.goalType()).isEqualTo("NEW_CUSTOMER_ACQUISITION");
    assertThat(result.additionalValue()).isEqualTo(50.0);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("goals"),
            operations.capture());

    Map<String, Object> create =
        (Map<String, Object>)
            ((Map<String, Object>) operations.getValue().getFirst()).get("create");
    assertThat(create.get("goalType")).isEqualTo("NEW_CUSTOMER_ACQUISITION");
    Map<String, Object> settings =
        (Map<String, Object>) create.get("newCustomerAcquisitionGoalSettings");
    Map<String, Object> values =
        (Map<String, Object>) settings.get("valueSettings");
    assertThat(values)
        .containsEntry("additionalValue", 50.0)
        .containsEntry("additionalHighLifetimeValue", 100.0);
  }

  @Test
  void rejectsConflictingCustomerListClassificationsBeforeMutation() {
    var context = context(10L, 20L, "1234567890");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    AdAudience audience = new AdAudience();
    audience.setId(101L);
    audience.setCompanyId(10L);
    audience.setAdAccountId(20L);
    audience.setProvider(AdProvider.GOOGLE_ADS);
    audience.setExternalAudienceId("501");
    audience.setName("Existing customers");
    audience.setAccessible(true);
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(audience));

    assertThatThrownBy(
            () ->
                service.replaceCustomerTypes(
                    10L,
                    20L,
                    new GoogleUserListCustomerTypeRequest(
                        101L, List.of("PURCHASERS", "CONVERTED_LEADS")),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("conflict");

    verify(apiClient, never())
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("userListCustomerTypes"),
            org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void launchValidationRequiresConfiguredGoalAndClassifiedList() throws Exception {
    var context = context(10L, 20L, "1234567890");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("FROM goal")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "goal": {
                            "resourceName": "customers/1234567890/goals/55",
                            "goalId": "55",
                            "goalType": "NEW_CUSTOMER_ACQUISITION",
                            "newCustomerAcquisitionGoalSettings": {
                              "valueSettings": {"additionalValue": 50.0}
                            }
                          }
                        }
                        """));
              }
              if (query.contains("FROM user_list_customer_type")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "userListCustomerType": {
                            "resourceName": "customers/1234567890/userListCustomerTypes/501~ALL_CUSTOMERS",
                            "userList": "customers/1234567890/userLists/501",
                            "customerTypeCategory": "ALL_CUSTOMERS"
                          }
                        }
                        """));
              }
              return List.of();
            });

    when(audienceRepository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(
            10L, 20L))
        .thenReturn(List.of());

    service.validateCampaignConfiguration(
        10L,
        20L,
        new GooglePerformanceMaxLifecycleRequest(
            "NEW_CUSTOMER_ACQUISITION",
            "TARGET_ALL",
            60.0,
            null,
            null,
            null,
            null));
  }

  @Test
  void targetSpecificRejectsCampaignValueOverride() {
    assertThatThrownBy(
            () ->
                service.validateCampaignConfiguration(
                    10L,
                    20L,
                    new GooglePerformanceMaxLifecycleRequest(
                        "NEW_CUSTOMER_ACQUISITION",
                        "TARGET_SPECIFIC",
                        60.0,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("cannot use campaign value overrides");
  }

  private GoogleAdsAccountContextService.Context context(
      Long companyId, Long accountId, String externalAccountId) {
    AdAccount account = new AdAccount();
    account.setId(accountId);
    account.setCompanyId(companyId);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId(externalAccountId);
    account.setManaged(true);
    account.setAccessible(true);
    return new GoogleAdsAccountContextService.Context(
        account,
        new AdProviderConnection(),
        "access-token",
        null,
        false,
        false);
  }
}
