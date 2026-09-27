package com.messaging.ads.meta;

import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaAdRequest;
import com.messaging.ads.meta.dto.CreateMetaCatalogAdRequest;
import com.messaging.ads.meta.dto.UpdateMetaAdRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsAdMutationService {
  private final AdSetRepository adSetRepository;
  private final AdRepository adRepository;
  private final AdCreativeRepository creativeRepository;
  private final AdAccountResourceRepository resourceRepository;
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsAdPersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdResponse create(Long companyId, Long adSetId, CreateMetaAdRequest request) {
    AdSet adSet = requireAdSet(companyId, adSetId);
    AdAccount account = requireManagedAccount(companyId, adSet.getAdAccountId());
    AdCreative creative = requireCreative(companyId, account.getId(), request.creativeId());
    String token = accessToken(companyId, account);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name().trim());
    payload.put("adset_id", adSet.getExternalAdSetId());
    payload.put("creative", Map.of("creative_id", creative.getExternalCreativeId()));
    payload.put("status", "PAUSED");

    String externalAdId = apiClient.createAd(token, account.getExternalAccountId(), payload);
    return AdResponse.from(
        persistenceService.persistOne(companyId, adSetId, apiClient.ad(token, externalAdId)));
  }

  public AdResponse createCatalog(
      Long companyId,
      Long adSetId,
      CreateMetaCatalogAdRequest request,
      Long actorUserId) {
    AdSet adSet = requireAdSet(companyId, adSetId);
    AdAccount account = requireManagedAccount(companyId, adSet.getAdAccountId());
    validateResource(
        companyId,
        account.getId(),
        com.messaging.ads.enums.AdAccountResourceType.FACEBOOK_PAGE,
        request.pageId(),
        "Selected Facebook Page is not available for this Meta ad account");
    if (request.instagramActorId() != null && !request.instagramActorId().isBlank()) {
      validateResource(
          companyId,
          account.getId(),
          com.messaging.ads.enums.AdAccountResourceType.INSTAGRAM_ACCOUNT,
          request.instagramActorId(),
          "Selected Instagram account is not available for this Meta ad account");
    }
    validateResource(
        companyId,
        account.getId(),
        com.messaging.ads.enums.AdAccountResourceType.META_PRODUCT_SET,
        request.productSetId(),
        "Selected Meta product set is not available for this ad account");

    String token = accessToken(companyId, account);
    Map<String, Object> templateData = new LinkedHashMap<>();
    templateData.put(
        "call_to_action",
        Map.of("type", normalizeCta(request.callToActionType())));
    templateData.put("link", requireUrl(request.destinationUrl()));
    if (request.headline() != null && !request.headline().isBlank()) {
      templateData.put("name", request.headline().trim());
    }

    Map<String, Object> story = new LinkedHashMap<>();
    story.put("page_id", request.pageId().trim());
    if (request.instagramActorId() != null && !request.instagramActorId().isBlank()) {
      story.put("instagram_user_id", request.instagramActorId().trim());
    }
    story.put("template_data", templateData);

    Map<String, Object> inlineCreative = new LinkedHashMap<>();
    inlineCreative.put("product_set_id", request.productSetId().trim());
    inlineCreative.put("object_story_spec", story);
    if (Boolean.TRUE.equals(request.backgroundGeneration())) {
      inlineCreative.put(
          "degrees_of_freedom_spec",
          Map.of(
              "creative_features_spec",
              Map.of(
                  "image_background_gen",
                  Map.of("enroll_status", "OPT_IN"))));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name().trim());
    payload.put("adset_id", adSet.getExternalAdSetId());
    payload.put("creative", inlineCreative);
    payload.put("status", "PAUSED");

    String externalAdId =
        apiClient.createAd(token, account.getExternalAccountId(), payload);
    AdResponse created =
        AdResponse.from(
            persistenceService.persistOne(
                companyId, adSetId, apiClient.ad(token, externalAdId)));

    activityLogService.record(
        companyId,
        account.getId(),
        AdProvider.META_ADS,
        actorUserId,
        "META_CATALOG_AD_CREATED",
        "AD",
        externalAdId,
        null,
        created,
        Map.of(
            "catalogAd", true,
            "productSetId", request.productSetId().trim(),
            "backgroundGeneration", Boolean.TRUE.equals(request.backgroundGeneration())));
    return created;
  }

  public AdResponse update(Long companyId, Long adId, UpdateMetaAdRequest request) {
    Ad ad = requireAd(companyId, adId);
    AdSet adSet = requireAdSet(companyId, ad.getAdSetId());
    AdAccount account = requireManagedAccount(companyId, ad.getAdAccountId());
    Map<String, Object> updates = new LinkedHashMap<>();
    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Ad name cannot be blank");
      updates.put("name", request.name().trim());
    }
    if (request.creativeId() != null) {
      AdCreative creative = requireCreative(companyId, account.getId(), request.creativeId());
      updates.put("creative", Map.of("creative_id", creative.getExternalCreativeId()));
    }
    if (updates.isEmpty()) throw new BadRequestException("No ad changes were supplied");

    String token = accessToken(companyId, account);
    apiClient.updateAd(token, ad.getExternalAdId(), updates);
    return AdResponse.from(
        persistenceService.persistOne(companyId, adSet.getId(), apiClient.ad(token, ad.getExternalAdId())));
  }

  public AdResponse setStatus(Long companyId, Long adId, String requestedStatus) {
    Ad ad = requireAd(companyId, adId);
    AdSet adSet = requireAdSet(companyId, ad.getAdSetId());
    AdAccount account = requireManagedAccount(companyId, ad.getAdAccountId());
    String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
    if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException("Ad status must be ACTIVE or PAUSED");
    }
    String token = accessToken(companyId, account);
    apiClient.updateAd(token, ad.getExternalAdId(), Map.of("status", status));
    return AdResponse.from(
        persistenceService.persistOne(companyId, adSet.getId(), apiClient.ad(token, ad.getExternalAdId())));
  }

  private void validateResource(
      Long companyId,
      Long accountId,
      com.messaging.ads.enums.AdAccountResourceType type,
      String externalId,
      String message) {
    if (externalId == null || externalId.isBlank()) {
      throw new BadRequestException(message);
    }
    var resource =
        resourceRepository
            .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                companyId, accountId, type, externalId.trim())
            .orElse(null);
    if (resource == null || !resource.isAccessible()) {
      throw new BadRequestException(message);
    }
  }

  private String requireUrl(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Destination URL is required");
    }
    String normalized = value.trim();
    if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
      throw new BadRequestException("Destination URL must start with http:// or https://");
    }
    return normalized;
  }

  private String normalizeCta(String value) {
    if (value == null || value.isBlank()) return "SHOP_NOW";
    return value.trim().toUpperCase();
  }

  private Ad requireAd(Long companyId, Long adId) {
    Ad ad = adRepository.findByIdAndCompanyId(adId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising ad not found"));
    if (ad.getProvider() != AdProvider.META_ADS || !ad.isAccessible()) {
      throw new BadRequestException("Meta ad is not available for management");
    }
    return ad;
  }

  private AdSet requireAdSet(Long companyId, Long adSetId) {
    AdSet adSet = adSetRepository.findByIdAndCompanyId(adSetId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising ad set not found"));
    if (adSet.getProvider() != AdProvider.META_ADS || !adSet.isAccessible()) {
      throw new BadRequestException("Meta ad set is not available for ad management");
    }
    return adSet;
  }

  private AdCreative requireCreative(Long companyId, Long accountId, String creativeId) {
    Long internalId;
    try {
      internalId = Long.valueOf(creativeId);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Creative ID is invalid");
    }
    AdCreative creative = creativeRepository.findByIdAndCompanyId(internalId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising creative not found"));
    if (creative.getProvider() != AdProvider.META_ADS
        || !creative.isAccessible()
        || !accountId.equals(creative.getAdAccountId())) {
      throw new BadRequestException("Creative does not belong to this Meta ad account");
    }
    return creative;
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
}
