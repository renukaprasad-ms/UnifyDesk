package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountHealthSummary;
import com.messaging.ads.repository.AdAccountHealthRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsHealthPersistenceServiceTests {

  @Mock private AdAccountHealthRepository healthRepository;

  private MetaAdsHealthPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsHealthPersistenceService(
            healthRepository, new ObjectMapper());
  }

  @Test
  void persistsMetaAccountHealthAndDeliveryChecks() throws Exception {
    when(healthRepository.findByAdAccountIdAndProvider(20L, AdProvider.META_ADS))
        .thenReturn(Optional.empty());
    when(healthRepository.save(any(AdAccountHealth.class)))
        .thenAnswer(
            invocation -> {
              AdAccountHealth health = invocation.getArgument(0);
              health.setId(99L);
              return health;
            });

    ObjectMapper mapper = new ObjectMapper();
    var checks =
        mapper.readTree(
            "[{\"check_name\":\"PAYMENT_METHOD\","
                + "\"summary\":\"Payment method needs attention\","
                + "\"description\":\"Resolve the payment issue in Meta.\"}]");

    AdAccountHealthSummary summary =
        new AdAccountHealthSummary(
            "act_123456",
            "123456",
            "Acme Ads",
            1,
            null,
            new BigDecimal("82.5"),
            10,
            List.of("CAN_CREATE_ADS"),
            List.of("CAN_CREATE_ADS", "CAN_VIEW_INSIGHTS"),
            List.of("ADVERTISE", "ANALYZE"),
            checks,
            true,
            true,
            false,
            true,
            "2026-10-01T12:00:00Z",
            1);

    var response = service.persist(10L, 20L, summary);

    assertThat(response.synced()).isTrue();
    assertThat(response.pageAuthorized()).isTrue();
    assertThat(response.offsitePixelsTosAccepted()).isTrue();
    assertThat(response.opportunityScore()).isEqualByComparingTo("82.5");
    assertThat(response.capabilities()).containsExactly("CAN_CREATE_ADS");
    assertThat(response.allCapabilities())
        .containsExactly("CAN_CREATE_ADS", "CAN_VIEW_INSIGHTS");
    assertThat(response.userTasks()).containsExactly("ADVERTISE", "ANALYZE");
    assertThat(response.failedDeliveryChecks()).hasSize(1);
    assertThat(response.accessExpiresAt()).isNotNull();

    ArgumentCaptor<AdAccountHealth> captor =
        ArgumentCaptor.forClass(AdAccountHealth.class);
    org.mockito.Mockito.verify(healthRepository).save(captor.capture());
    assertThat(captor.getValue().getFailedDeliveryChecksJson())
        .contains("PAYMENT_METHOD");
  }
}
