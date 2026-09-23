package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsInsightBreakdownSyncServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsInsightBreakdownPersistenceService persistenceService;

  private MetaAdsInsightBreakdownSyncService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsInsightBreakdownSyncService(
            accountRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService);
  }

  @Test
  void continuesWhenOneMetaBreakdownFails() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("123456");
    account.setManaged(true);
    account.setAccessible(true);

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);

    when(accountRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(30L))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));

    when(apiClient.insightBreakdown(
            anyString(), anyString(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              String breakdown = invocation.getArgument(4);
              if ("region".equals(breakdown)) {
                throw new RuntimeException("Meta breakdown unavailable");
              }
              return List.of();
            });
    when(persistenceService.persist(any(), any(), any(), any(), any(), any()))
        .thenReturn(0);

    var response =
        service.sync(
            10L,
            20L,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 7));

    assertThat(response.failedBreakdowns()).containsExactly("REGION");
    assertThat(response.totalRows()).isZero();
  }
}
