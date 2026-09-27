package com.messaging.ads.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsAudienceMutationService;
import com.messaging.ads.google.GoogleDataManagerService;
import com.messaging.ads.google.dto.GoogleDataManagerUploadResponse;
import com.messaging.ads.meta.MetaAdsAudienceMutationService;
import com.messaging.ads.meta.MetaAdsCustomerListService;
import com.messaging.ads.meta.dto.MetaCustomerListMembersRequest;
import com.messaging.ads.meta.dto.MetaCustomerListMembersResponse;
import com.messaging.ads.optimization.dto.FirstPartyAudienceBuildRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirstPartyAudienceBuilderServiceTests {

  @Mock private GoogleAdsAudienceMutationService googleAudienceMutationService;
  @Mock private GoogleDataManagerService googleDataManagerService;
  @Mock private MetaAdsAudienceMutationService metaAudienceMutationService;
  @Mock private MetaAdsCustomerListService metaCustomerListService;

  private FirstPartyAudienceBuilderService service;

  @BeforeEach
  void setUp() {
    service =
        new FirstPartyAudienceBuilderService(
            googleAudienceMutationService,
            googleDataManagerService,
            metaAudienceMutationService,
            metaCustomerListService);
  }

  @Test
  void fansOneConsentedAudienceOutToGoogleAndMeta() {
    AdAudienceResponse google =
        new AdAudienceResponse(
            "101", "10", "20", AdProvider.GOOGLE_ADS, "g-list", "VIP", "CUSTOMER_MATCH",
            null, 30, null, null, null, null, null, null, null, null, null, null, true, null);
    AdAudienceResponse meta =
        new AdAudienceResponse(
            "102", "10", "21", AdProvider.META_ADS, "m-list", "VIP", "CUSTOM",
            null, null, null, null, null, null, null, null, null, "USER_PROVIDED_ONLY", null, null, true, null);
    when(googleAudienceMutationService.create(eq(10L), eq(20L), any(), eq(77L)))
        .thenReturn(google);
    when(metaAudienceMutationService.create(eq(10L), eq(21L), any(), eq(77L)))
        .thenReturn(meta);
    when(googleDataManagerService.uploadAudienceMembers(eq(10L), eq(101L), any(), eq(77L)))
        .thenReturn(new GoogleDataManagerUploadResponse(List.of("req-1"), 1, false, List.of()));
    when(metaCustomerListService.mutateMembers(eq(10L), eq(102L), any(), eq(77L)))
        .thenReturn(
            new MetaCustomerListMembersResponse(
                "m-list", MetaCustomerListMembersRequest.Operation.ADD, 1, 1, 1, 0));

    var response =
        service.build(
            10L,
            new FirstPartyAudienceBuildRequest(
                "VIP",
                "Consented high-value customers",
                30,
                List.of(
                    new FirstPartyAudienceBuildRequest.Target(AdProvider.GOOGLE_ADS, 20L),
                    new FirstPartyAudienceBuildRequest.Target(AdProvider.META_ADS, 21L)),
                List.of(
                    new FirstPartyAudienceBuildRequest.Member(
                        "user@example.com", "+919876543210", "crm-1")),
                true,
                true,
                true,
                true),
            77L);

    assertThat(response.submittedMembers()).isEqualTo(1);
    assertThat(response.targets()).hasSize(2);
    assertThat(response.targets()).allMatch(value -> value.success());
    assertThat(response.targets()).extracting(value -> value.externalAudienceId())
        .containsExactlyInAnyOrder("g-list", "m-list");
  }
}
