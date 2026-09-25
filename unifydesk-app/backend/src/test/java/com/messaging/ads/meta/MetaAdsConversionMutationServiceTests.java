package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CustomConversionSummary;
import com.messaging.ads.meta.MetaAdsApiClient.PixelRef;
import com.messaging.ads.meta.dto.CreateMetaConversionRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsConversionMutationServiceTests {

  private static final Long COMPANY_ID = 10L;
  private static final Long ACCOUNT_ID = 20L;
  private static final Long CONNECTION_ID = 30L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdAccountResourceRepository resourceRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsConversionPersistenceService persistenceService;

  private MetaAdsConversionMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsConversionMutationService(
            accountRepository,
            resourceRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService,
            new ObjectMapper());
  }

  @Test
  void createsPixelBackedWebsiteCustomConversion() {
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
    when(apiClient.createCustomConversion(eq("token"), eq("123456"), payload.capture()))
        .thenReturn("conversion-external");

    CustomConversionSummary snapshot =
        new CustomConversionSummary(
            "conversion-external",
            "123456",
            "Purchase thank-you",
            "Purchase completed",
            "PURCHASE",
            "PIXEL",
            new PixelRef("pixel-1", "Website Pixel"),
            null,
            null,
            new BigDecimal("499.00"),
            false,
            false,
            null,
            null,
            "1720000000");
    when(apiClient.customConversion("token", "conversion-external")).thenReturn(snapshot);

    AdConversion stored = new AdConversion();
    stored.setId(88L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalConversionId("conversion-external");
    stored.setName("Purchase thank-you");
    stored.setCustomEventType("PURCHASE");
    stored.setPixelId("pixel-1");
    stored.setAccessible(true);
    when(persistenceService.persistOne(COMPANY_ID, ACCOUNT_ID, snapshot)).thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            ACCOUNT_ID,
            new CreateMetaConversionRequest(
                "Purchase thank-you",
                "Purchase completed",
                "PURCHASE",
                "pixel-1",
                "{\"url\":{\"i_contains\":\"/thank-you\"}}",
                new BigDecimal("499.00")));

    assertThat(response.externalConversionId()).isEqualTo("conversion-external");
    assertThat(payload.getValue())
        .containsEntry("custom_event_type", "PURCHASE")
        .containsEntry("event_source_id", "pixel-1")
        .containsEntry("action_source_type", "website")
        .containsEntry("rule", "{\"url\":{\"i_contains\":\"/thank-you\"}}")
        .containsEntry("default_conversion_value", new BigDecimal("499.00"));
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
