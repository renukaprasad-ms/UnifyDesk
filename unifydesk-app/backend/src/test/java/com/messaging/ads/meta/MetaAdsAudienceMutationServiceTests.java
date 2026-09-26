package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CustomAudienceSummary;
import com.messaging.ads.meta.dto.CreateMetaAudienceRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdActivityLogService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsAudienceMutationServiceTests {

  private static final Long COMPANY_ID = 10L;
  private static final Long ACCOUNT_ID = 20L;
  private static final Long CONNECTION_ID = 30L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdAccountResourceRepository resourceRepository;
  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsAudiencePersistenceService persistenceService;
  @Mock private AdActivityLogService activityLogService;

  private MetaAdsAudienceMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsAudienceMutationService(
            accountRepository,
            resourceRepository,
            audienceRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService,
            new ObjectMapper(),
            activityLogService);
  }

  @Test
  void createsWebsiteAudienceFromAccessiblePixel() {
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(resourceRepository.findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
            COMPANY_ID, ACCOUNT_ID, AdAccountResourceType.META_PIXEL, "pixel-1"))
        .thenReturn(Optional.of(pixel()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createCustomAudience(eq("token"), eq("123456"), payload.capture()))
        .thenReturn("audience-external");

    CustomAudienceSummary snapshot =
        new CustomAudienceSummary(
            "audience-external",
            "Website visitors",
            "123456",
            "WEBSITE",
            "Recent visitors",
            30,
            null,
            null,
            null,
            null,
            "pixel-1",
            null,
            null,
            "1720000000",
            "1720000001");
    when(apiClient.customAudience("token", "audience-external")).thenReturn(snapshot);

    AdAudience stored = new AdAudience();
    stored.setId(99L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalAudienceId("audience-external");
    stored.setName("Website visitors");
    stored.setSubtype("WEBSITE");
    stored.setAccessible(true);
    when(persistenceService.persistOne(COMPANY_ID, ACCOUNT_ID, snapshot)).thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            ACCOUNT_ID,
            new CreateMetaAudienceRequest(
                "Website visitors",
                "WEBSITE",
                "Recent visitors",
                "pixel-1",
                "{\"url\":{\"i_contains\":\"/pricing\"}}",
                30,
                null,
                null,
                null,
                null),
            77L);

    assertThat(response.externalAudienceId()).isEqualTo("audience-external");
    assertThat(payload.getValue())
        .containsEntry("subtype", "WEBSITE")
        .containsEntry("pixel_id", "pixel-1")
        .containsEntry("retention_days", 30)
        .containsEntry("rule", "{\"url\":{\"i_contains\":\"/pricing\"}}");
  }


  @Test
  void createsCustomerListAudienceWithDeclaredFileSource() {
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createCustomAudience(eq("token"), eq("123456"), payload.capture()))
        .thenReturn("audience-custom");

    CustomAudienceSummary snapshot =
        new CustomAudienceSummary(
            "audience-custom",
            "CRM customers",
            "123456",
            "CUSTOM",
            "First-party customers",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "1720000000",
            "1720000001");
    when(apiClient.customAudience("token", "audience-custom")).thenReturn(snapshot);

    AdAudience stored = new AdAudience();
    stored.setId(100L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalAudienceId("audience-custom");
    stored.setName("CRM customers");
    stored.setSubtype("CUSTOM");
    stored.setAccessible(true);
    when(persistenceService.persistOne(COMPANY_ID, ACCOUNT_ID, snapshot)).thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            ACCOUNT_ID,
            new CreateMetaAudienceRequest(
                "CRM customers",
                "CUSTOMER_LIST",
                "First-party customers",
                null,
                null,
                null,
                null,
                null,
                null,
                "USER_PROVIDED_ONLY"),
            77L);

    assertThat(response.subtype()).isEqualTo("CUSTOM");
    assertThat(payload.getValue())
        .containsEntry("subtype", "CUSTOM")
        .containsEntry("customer_file_source", "USER_PROVIDED_ONLY");
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(ACCOUNT_ID);
    account.setCompanyId(COMPANY_ID);
    account.setConnectionId(CONNECTION_ID);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }

  private AdAccountResource pixel() {
    AdAccountResource pixel = new AdAccountResource();
    pixel.setCompanyId(COMPANY_ID);
    pixel.setAdAccountId(ACCOUNT_ID);
    pixel.setProvider(AdProvider.META_ADS);
    pixel.setResourceType(AdAccountResourceType.META_PIXEL);
    pixel.setExternalResourceId("pixel-1");
    pixel.setAccessible(true);
    return pixel;
  }

  private AdProviderConnection connection() {
    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);
    return connection;
  }
}
