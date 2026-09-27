package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CreativeSummary;
import com.messaging.ads.meta.dto.CreateMetaCreativeRequest;
import com.messaging.ads.meta.dto.CreateMetaPlacementCreativeRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
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
class MetaAdsCreativeMutationServiceTests {
  private static final Long COMPANY_ID = 10L;
  private static final Long ACCOUNT_ID = 20L;
  private static final Long CONNECTION_ID = 30L;

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdAccountResourceRepository resourceRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;
  @Mock private MetaAdsCreativePersistenceService persistenceService;
  @Mock private AdActivityLogService activityLogService;

  private MetaAdsCreativeMutationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsCreativeMutationService(
            accountRepository,
            resourceRepository,
            connectionRepository,
            tokenService,
            apiClient,
            persistenceService,
            activityLogService);
  }

  @Test
  void createsSingleImageCreativeWithValidatedIdentity() {
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(resourceRepository.findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
            COMPANY_ID, ACCOUNT_ID, AdAccountResourceType.FACEBOOK_PAGE, "page-1"))
        .thenReturn(Optional.of(resource(AdAccountResourceType.FACEBOOK_PAGE, "page-1")));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createCreative(eq("token"), eq("123456"), payload.capture()))
        .thenReturn("creative-1");
    CreativeSummary snapshot =
        new CreativeSummary(
            "creative-1",
            "Launch creative",
            "123456",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(apiClient.creative("token", "creative-1")).thenReturn(snapshot);

    AdCreative stored = new AdCreative();
    stored.setId(99L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalCreativeId("creative-1");
    stored.setName("Launch creative");
    when(persistenceService.persistOne(COMPANY_ID, ACCOUNT_ID, snapshot)).thenReturn(stored);

    var response =
        service.create(
            COMPANY_ID,
            ACCOUNT_ID,
            new CreateMetaCreativeRequest(
                "Launch creative",
                "SINGLE_IMAGE",
                "page-1",
                null,
                "Primary text",
                "Headline",
                "Description",
                "LEARN_MORE",
                "https://example.com",
                "image-hash",
                null,
                null,
                null,
                "utm_source=meta",
                true,
                List.of()),
            77L);

    assertThat(response.externalCreativeId()).isEqualTo("creative-1");
    @SuppressWarnings("unchecked")
    Map<String, Object> story = (Map<String, Object>) payload.getValue().get("object_story_spec");
    assertThat(story.get("page_id")).isEqualTo("page-1");
    @SuppressWarnings("unchecked")
    Map<String, Object> linkData = (Map<String, Object>) story.get("link_data");
    assertThat(linkData.get("image_hash")).isEqualTo("image-hash");
    assertThat(linkData.get("link")).isEqualTo("https://example.com");
    @SuppressWarnings("unchecked")
    Map<String, Object> degrees =
        (Map<String, Object>) payload.getValue().get("degrees_of_freedom_spec");
    @SuppressWarnings("unchecked")
    Map<String, Object> creativeFeatures =
        (Map<String, Object>) degrees.get("creative_features_spec");
    assertThat(creativeFeatures)
        .extracting("standard_enhancements")
        .isEqualTo(Map.of("enroll_status", "OPT_IN"));
  }

  @Test
  void createsPlacementCustomizedCreativeWithLabeledAssets() {
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(resourceRepository.findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
            COMPANY_ID, ACCOUNT_ID, AdAccountResourceType.FACEBOOK_PAGE, "page-1"))
        .thenReturn(Optional.of(resource(AdAccountResourceType.FACEBOOK_PAGE, "page-1")));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(tokenService.read(CONNECTION_ID))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken("token", "bearer", "v26.0", "meta-user"), null));

    when(apiClient.uploadVideoFromUrl(
            "token", "123456", "https://cdn.example.com/story.mp4"))
        .thenReturn("video-1");
    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    when(apiClient.createCreative(eq("token"), eq("123456"), payload.capture()))
        .thenReturn("creative-placement");
    CreativeSummary snapshot =
        new CreativeSummary(
            "creative-placement",
            "Placement creative",
            "123456",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(apiClient.creative("token", "creative-placement")).thenReturn(snapshot);

    AdCreative stored = new AdCreative();
    stored.setId(100L);
    stored.setCompanyId(COMPANY_ID);
    stored.setAdAccountId(ACCOUNT_ID);
    stored.setProvider(AdProvider.META_ADS);
    stored.setExternalCreativeId("creative-placement");
    stored.setName("Placement creative");
    when(persistenceService.persistOne(COMPANY_ID, ACCOUNT_ID, snapshot)).thenReturn(stored);

    var response =
        service.createPlacementCustomized(
            COMPANY_ID,
            ACCOUNT_ID,
            new CreateMetaPlacementCreativeRequest(
                "Placement creative",
                "page-1",
                null,
                "LEARN_MORE",
                "utm_source=meta",
                true,
                List.of(
                    new CreateMetaPlacementCreativeRequest.PlacementAsset(
                        List.of("facebook", "instagram"),
                        List.of("feed"),
                        List.of("stream"),
                        List.of(),
                        List.of(),
                        "Feed body",
                        "Feed title",
                        "Feed description",
                        "https://example.com/feed",
                        "IMAGE",
                        "https://cdn.example.com/feed.jpg"),
                    new CreateMetaPlacementCreativeRequest.PlacementAsset(
                        List.of("facebook", "instagram"),
                        List.of("story"),
                        List.of("story"),
                        List.of(),
                        List.of(),
                        "Story body",
                        "Story title",
                        "Story description",
                        "https://example.com/story",
                        "VIDEO",
                        "https://cdn.example.com/story.mp4"))),
            77L);

    assertThat(response.externalCreativeId()).isEqualTo("creative-placement");

    @SuppressWarnings("unchecked")
    Map<String, Object> assetFeed =
        (Map<String, Object>) payload.getValue().get("asset_feed_spec");
    assertThat(assetFeed.get("optimization_type")).isEqualTo("PLACEMENT");
    assertThat(assetFeed.get("ad_formats"))
        .isEqualTo(List.of("SINGLE_IMAGE", "SINGLE_VIDEO"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> videos =
        (List<Map<String, Object>>) assetFeed.get("videos");
    assertThat(videos).singleElement().satisfies(video -> assertThat(video.get("video_id")).isEqualTo("video-1"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rules =
        (List<Map<String, Object>>) assetFeed.get("asset_customization_rules");
    assertThat(rules).hasSize(2);
    @SuppressWarnings("unchecked")
    Map<String, Object> firstSpec =
        (Map<String, Object>) rules.getFirst().get("customization_spec");
    assertThat(firstSpec.get("facebook_positions")).isEqualTo(List.of("feed"));
    assertThat(firstSpec.get("instagram_positions")).isEqualTo(List.of("stream"));
    assertThat(rules.getFirst()).containsKeys("image_label", "body_label", "title_label", "link_url_label");
    assertThat(rules.get(1)).containsKey("video_label");
  }

  @Test
  void rejectsPageThatDoesNotBelongToAdAccount() {
    when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
        .thenReturn(Optional.of(account()));
    when(resourceRepository.findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
            COMPANY_ID, ACCOUNT_ID, AdAccountResourceType.FACEBOOK_PAGE, "wrong-page"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    COMPANY_ID,
                    ACCOUNT_ID,
                    new CreateMetaCreativeRequest(
                        "Creative",
                        "SINGLE_IMAGE",
                        "wrong-page",
                        null,
                        null,
                        null,
                        null,
                        "LEARN_MORE",
                        "https://example.com",
                        "hash",
                        null,
                        null,
                        null,
                        null,
                        List.of()),
                    77L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Facebook Page");
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

  private AdAccountResource resource(AdAccountResourceType type, String externalId) {
    AdAccountResource resource = new AdAccountResource();
    resource.setCompanyId(COMPANY_ID);
    resource.setAdAccountId(ACCOUNT_ID);
    resource.setProvider(AdProvider.META_ADS);
    resource.setResourceType(type);
    resource.setExternalResourceId(externalId);
    resource.setAccessible(true);
    return resource;
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
