package com.messaging.ads.meta;

import com.messaging.ads.dto.AdCreativeResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaCreativeRequest;
import com.messaging.ads.meta.dto.CreateMetaPlacementCreativeRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsCreativeMutationService {
  private final AdAccountRepository accountRepository;
  private final AdAccountResourceRepository resourceRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsCreativePersistenceService persistenceService;

  public AdCreativeResponse create(
      Long companyId, Long accountId, CreateMetaCreativeRequest request) {
    AdAccount account = requireManagedAccount(companyId, accountId);
    validateIdentity(companyId, accountId, request.pageId(), request.instagramActorId());

    String format = request.format().trim().toUpperCase();
    String token = accessToken(companyId, account);
    Map<String, Object> story = new LinkedHashMap<>();
    story.put("page_id", request.pageId().trim());
    if (hasText(request.instagramActorId())) {
      story.put("instagram_actor_id", request.instagramActorId().trim());
    }

    switch (format) {
      case "SINGLE_IMAGE" -> story.put("link_data", singleImageData(request));
      case "VIDEO" ->
          story.put(
              "video_data",
              videoData(request, token, account.getExternalAccountId()));
      case "CAROUSEL" -> story.put("link_data", carouselData(request));
      default -> throw new BadRequestException("Unsupported Meta creative format: " + format);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name().trim());
    payload.put("object_story_spec", story);
    if (hasText(request.urlTags())) payload.put("url_tags", request.urlTags().trim());

    String externalId = apiClient.createCreative(token, account.getExternalAccountId(), payload);
    return AdCreativeResponse.from(
        persistenceService.persistOne(companyId, accountId, apiClient.creative(token, externalId)));
  }

  public AdCreativeResponse createPlacementCustomized(
      Long companyId, Long accountId, CreateMetaPlacementCreativeRequest request) {
    AdAccount account = requireManagedAccount(companyId, accountId);
    validateIdentity(companyId, accountId, request.pageId(), request.instagramActorId());
    if (request.placementAssets() == null || request.placementAssets().size() < 2) {
      throw new BadRequestException(
          "Meta placement customization requires at least two placement assets");
    }

    String token = accessToken(companyId, account);
    Map<String, Object> story = new LinkedHashMap<>();
    story.put("page_id", request.pageId().trim());
    if (hasText(request.instagramActorId())) {
      story.put("instagram_actor_id", request.instagramActorId().trim());
    }

    List<Map<String, Object>> images = new ArrayList<>();
    List<Map<String, Object>> videos = new ArrayList<>();
    List<Map<String, Object>> bodies = new ArrayList<>();
    List<Map<String, Object>> titles = new ArrayList<>();
    List<Map<String, Object>> descriptions = new ArrayList<>();
    List<Map<String, Object>> linkUrls = new ArrayList<>();
    List<Map<String, Object>> rules = new ArrayList<>();

    int priority = 1;
    for (CreateMetaPlacementCreativeRequest.PlacementAsset asset : request.placementAssets()) {
      String baseLabel = "placement_" + priority;
      Map<String, Object> mediaLabel = Map.of("name", baseLabel + "_media");
      Map<String, Object> bodyLabel = Map.of("name", baseLabel + "_body");
      Map<String, Object> titleLabel = Map.of("name", baseLabel + "_title");
      Map<String, Object> descriptionLabel = Map.of("name", baseLabel + "_description");
      Map<String, Object> linkLabel = Map.of("name", baseLabel + "_link");

      String mediaType = asset.mediaType().trim().toUpperCase();
      if ("IMAGE".equals(mediaType)) {
        images.add(
            Map.of(
                "url", asset.mediaUrl().trim(),
                "adlabels", List.of(mediaLabel)));
      } else if ("VIDEO".equals(mediaType)) {
        String videoId =
            apiClient.uploadVideoFromUrl(
                token, account.getExternalAccountId(), asset.mediaUrl().trim());
        videos.add(
            Map.of(
                "video_id", videoId,
                "adlabels", List.of(mediaLabel)));
      } else {
        throw new BadRequestException(
            "Meta placement creative media must be IMAGE or VIDEO");
      }

      if (hasText(asset.primaryText())) {
        bodies.add(
            Map.of(
                "text", asset.primaryText().trim(),
                "adlabels", List.of(bodyLabel)));
      }
      if (hasText(asset.headline())) {
        titles.add(
            Map.of(
                "text", asset.headline().trim(),
                "adlabels", List.of(titleLabel)));
      }
      if (hasText(asset.description())) {
        descriptions.add(
            Map.of(
                "text", asset.description().trim(),
                "adlabels", List.of(descriptionLabel)));
      }

      String destination = requireDestination(asset.destinationUrl());
      linkUrls.add(
          Map.of(
              "website_url", destination,
              "adlabels", List.of(linkLabel)));

      Map<String, Object> customization = new LinkedHashMap<>();
      putStringList(customization, "publisher_platforms", asset.publisherPlatforms());
      putStringList(customization, "facebook_positions", asset.facebookPositions());
      putStringList(customization, "instagram_positions", asset.instagramPositions());
      putStringList(customization, "messenger_positions", asset.messengerPositions());
      putStringList(
          customization, "audience_network_positions", asset.audienceNetworkPositions());
      if (!customization.containsKey("publisher_platforms")) {
        throw new BadRequestException(
            "Meta placement customization requires publisher platforms");
      }

      Map<String, Object> rule = new LinkedHashMap<>();
      rule.put("customization_spec", customization);
      rule.put("priority", priority);
      if ("IMAGE".equals(mediaType)) rule.put("image_label", mediaLabel);
      else rule.put("video_label", mediaLabel);
      if (hasText(asset.primaryText())) rule.put("body_label", bodyLabel);
      if (hasText(asset.headline())) rule.put("title_label", titleLabel);
      if (hasText(asset.description())) rule.put("description_label", descriptionLabel);
      rule.put("link_url_label", linkLabel);
      rules.add(rule);
      priority++;
    }

    Map<String, Object> assetFeed = new LinkedHashMap<>();
    if (!images.isEmpty()) assetFeed.put("images", images);
    if (!videos.isEmpty()) assetFeed.put("videos", videos);
    if (!bodies.isEmpty()) assetFeed.put("bodies", bodies);
    if (!titles.isEmpty()) assetFeed.put("titles", titles);
    if (!descriptions.isEmpty()) assetFeed.put("descriptions", descriptions);
    assetFeed.put("link_urls", linkUrls);
    if (hasText(request.callToActionType())) {
      assetFeed.put(
          "call_to_action_types",
          List.of(request.callToActionType().trim().toUpperCase()));
    }
    assetFeed.put("asset_customization_rules", rules);
    assetFeed.put("optimization_type", "PLACEMENT");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name().trim());
    payload.put("object_story_spec", story);
    payload.put("asset_feed_spec", assetFeed);
    if (hasText(request.urlTags())) payload.put("url_tags", request.urlTags().trim());

    String externalId =
        apiClient.createCreative(token, account.getExternalAccountId(), payload);
    return AdCreativeResponse.from(
        persistenceService.persistOne(
            companyId, accountId, apiClient.creative(token, externalId)));
  }

  private void putStringList(
      Map<String, Object> target, String field, List<String> values) {
    if (values == null || values.isEmpty()) return;
    List<String> normalized =
        values.stream()
            .filter(this::hasText)
            .map(value -> value.trim().toLowerCase())
            .distinct()
            .toList();
    if (!normalized.isEmpty()) target.put(field, normalized);
  }

  private Map<String, Object> singleImageData(CreateMetaCreativeRequest request) {
    String destination = requireDestination(request.destinationUrl());
    Map<String, Object> data = baseLinkData(request, destination);
    if (hasText(request.imageHash())) data.put("image_hash", request.imageHash().trim());
    else data.put("picture", request.imageUrl().trim());
    return data;
  }

  private Map<String, Object> videoData(
      CreateMetaCreativeRequest request, String token, String accountId) {
    String destination = requireDestination(request.destinationUrl());
    String videoId =
        hasText(request.videoId())
            ? request.videoId().trim()
            : apiClient.uploadVideoFromUrl(
                token, accountId, requireDestination(request.videoUrl()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("video_id", videoId);
    if (hasText(request.primaryText())) data.put("message", request.primaryText().trim());
    if (hasText(request.headline())) data.put("title", request.headline().trim());
    if (hasText(request.description())) data.put("description", request.description().trim());
    data.put("call_to_action", callToAction(request.callToActionType(), destination));
    return data;
  }

  private Map<String, Object> carouselData(CreateMetaCreativeRequest request) {
    String destination = requireDestination(request.destinationUrl());
    Map<String, Object> data = baseLinkData(request, destination);
    List<Map<String, Object>> cards = new ArrayList<>();
    for (CreateMetaCreativeRequest.CarouselCard card : request.carouselCards()) {
      Map<String, Object> item = new LinkedHashMap<>();
      String cardDestination =
          hasText(card.destinationUrl()) ? card.destinationUrl().trim() : destination;
      item.put("link", cardDestination);
      if (hasText(card.headline())) item.put("name", card.headline().trim());
      if (hasText(card.description())) item.put("description", card.description().trim());
      if (hasText(card.imageHash())) item.put("image_hash", card.imageHash().trim());
      else item.put("picture", card.imageUrl().trim());
      String cta = hasText(card.callToActionType()) ? card.callToActionType() : request.callToActionType();
      if (hasText(cta)) item.put("call_to_action", callToAction(cta, cardDestination));
      cards.add(item);
    }
    data.put("child_attachments", cards);
    return data;
  }

  private Map<String, Object> baseLinkData(
      CreateMetaCreativeRequest request, String destination) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("link", destination);
    if (hasText(request.primaryText())) data.put("message", request.primaryText().trim());
    if (hasText(request.headline())) data.put("name", request.headline().trim());
    if (hasText(request.description())) data.put("description", request.description().trim());
    if (hasText(request.callToActionType())) {
      data.put("call_to_action", callToAction(request.callToActionType(), destination));
    }
    return data;
  }

  private Map<String, Object> callToAction(String type, String destination) {
    Map<String, Object> cta = new LinkedHashMap<>();
    cta.put("type", hasText(type) ? type.trim().toUpperCase() : "LEARN_MORE");
    cta.put("value", Map.of("link", destination));
    return cta;
  }

  private void validateIdentity(
      Long companyId, Long accountId, String pageId, String instagramActorId) {
    var page = resourceRepository
        .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
            companyId, accountId, AdAccountResourceType.FACEBOOK_PAGE, pageId.trim())
        .orElseThrow(() -> new BadRequestException("Selected Facebook Page is not available for this ad account"));
    if (!page.isAccessible()) {
      throw new BadRequestException("Selected Facebook Page is no longer accessible");
    }
    if (hasText(instagramActorId)) {
      var instagram = resourceRepository
          .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
              companyId, accountId, AdAccountResourceType.INSTAGRAM_ACCOUNT, instagramActorId.trim())
          .orElseThrow(() -> new BadRequestException("Selected Instagram account is not available for this ad account"));
      if (!instagram.isAccessible()) {
        throw new BadRequestException("Selected Instagram account is no longer accessible");
      }
    }
  }

  private AdAccount requireManagedAccount(Long companyId, Long accountId) {
    AdAccount account = accountRepository.findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }
    return account;
  }

  private String accessToken(Long companyId, AdAccount account) {
    var connection = connectionRepository.findByIdAndCompanyId(account.getConnectionId(), companyId)
        .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    return tokenService.read(connection.getId()).token().accessToken();
  }

  private String requireDestination(String value) {
    if (!hasText(value)) throw new BadRequestException("Destination URL is required for this creative");
    String normalized = value.trim();
    if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
      throw new BadRequestException("Destination URL must start with http:// or https://");
    }
    return normalized;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
