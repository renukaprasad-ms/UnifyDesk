package com.messaging.ads.meta;

import com.messaging.ads.dto.AdCreativeResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaCreativeRequest;
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
    Map<String, Object> story = new LinkedHashMap<>();
    story.put("page_id", request.pageId().trim());
    if (hasText(request.instagramActorId())) {
      story.put("instagram_actor_id", request.instagramActorId().trim());
    }

    switch (format) {
      case "SINGLE_IMAGE" -> story.put("link_data", singleImageData(request));
      case "VIDEO" -> story.put("video_data", videoData(request));
      case "CAROUSEL" -> story.put("link_data", carouselData(request));
      default -> throw new BadRequestException("Unsupported Meta creative format: " + format);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name().trim());
    payload.put("object_story_spec", story);
    if (hasText(request.urlTags())) payload.put("url_tags", request.urlTags().trim());

    String token = accessToken(companyId, account);
    String externalId = apiClient.createCreative(token, account.getExternalAccountId(), payload);
    return AdCreativeResponse.from(
        persistenceService.persistOne(companyId, accountId, apiClient.creative(token, externalId)));
  }

  private Map<String, Object> singleImageData(CreateMetaCreativeRequest request) {
    String destination = requireDestination(request.destinationUrl());
    Map<String, Object> data = baseLinkData(request, destination);
    if (hasText(request.imageHash())) data.put("image_hash", request.imageHash().trim());
    else data.put("picture", request.imageUrl().trim());
    return data;
  }

  private Map<String, Object> videoData(CreateMetaCreativeRequest request) {
    String destination = requireDestination(request.destinationUrl());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("video_id", request.videoId().trim());
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
