package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleResponsiveSearchAdRequest;
import com.messaging.ads.google.dto.UpdateGoogleResponsiveSearchAdRequest;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
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
public class GoogleAdsAdMutationService {

  private final AdSetRepository adSetRepository;
  private final AdRepository adRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdSyncService syncService;
  private final GoogleAdsAdPersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdResponse create(
      Long companyId,
      Long adSetId,
      CreateGoogleResponsiveSearchAdRequest request,
      Long actorUserId) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());
    validateAssets(request.headlines(), request.descriptions());
    validateUrl(request.finalUrl());

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("name", request.name().trim());
    ad.put("finalUrls", List.of(request.finalUrl().trim()));
    ad.put(
        "responsiveSearchAd",
        rsa(
            request.headlines(),
            request.descriptions(),
            request.path1(),
            request.path2()));

    Map<String, Object> create = new LinkedHashMap<>();
    create.put(
        "adGroup",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "adGroups",
            adGroup.getExternalAdSetId()));
    create.put("status", "PAUSED");
    create.put("ad", ad);

    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "adGroupAds",
            List.of(Map.of("create", create)));

    String externalAdId = suffix(apiClient.firstMutatedResourceName(response));
    AdResponse after = refresh(context, adGroup, externalAdId);

    activityLogService.record(
        companyId,
        adGroup.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_RESPONSIVE_SEARCH_AD_CREATED",
        "AD",
        externalAdId,
        null,
        after,
        Map.of("initialStatus", "PAUSED"));
    return after;
  }

  public AdResponse update(
      Long companyId,
      Long adId,
      UpdateGoogleResponsiveSearchAdRequest request,
      Long actorUserId) {
    Ad stored = requireAd(companyId, adId);
    AdSet adGroup = requireAdGroup(companyId, stored.getAdSetId());
    var context = contextService.requireManaged(companyId, stored.getAdAccountId());
    AdResponse before = AdResponse.from(stored);

    JsonNode current =
        syncService.ad(context, adGroup.getExternalAdSetId(), stored.getExternalAdId());
    JsonNode currentAd = current.path("adGroupAd").path("ad");
    String providerAdId = currentAd.path("id").asText(null);
    if (providerAdId == null || !providerAdId.matches("\\d+")) {
      throw new BadRequestException("Google ad resource ID is unavailable");
    }

    Map<String, Object> update = new LinkedHashMap<>();
    update.put(
        "resourceName",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(), "ads", providerAdId));
    List<String> masks = new ArrayList<>();

    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Ad name cannot be blank");
      update.put("name", request.name().trim());
      masks.add("name");
    }
    if (request.finalUrl() != null) {
      validateUrl(request.finalUrl());
      update.put("finalUrls", List.of(request.finalUrl().trim()));
      masks.add("final_urls");
    }

    boolean rsaChanged =
        request.headlines() != null
            || request.descriptions() != null
            || request.path1() != null
            || request.path2() != null;
    if (rsaChanged) {
      List<String> headlines =
          request.headlines() != null
              ? request.headlines()
              : textAssets(currentAd.path("responsiveSearchAd").path("headlines"));
      List<String> descriptions =
          request.descriptions() != null
              ? request.descriptions()
              : textAssets(currentAd.path("responsiveSearchAd").path("descriptions"));
      validateAssets(headlines, descriptions);
      String path1 =
          request.path1() != null
              ? request.path1()
              : nullableText(currentAd.path("responsiveSearchAd"), "path1");
      String path2 =
          request.path2() != null
              ? request.path2()
              : nullableText(currentAd.path("responsiveSearchAd"), "path2");
      update.put("responsiveSearchAd", rsa(headlines, descriptions, path1, path2));
      masks.add("responsive_search_ad");
    }

    if (masks.isEmpty()) {
      throw new BadRequestException("No Google ad changes were supplied");
    }

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "ads",
        List.of(Map.of("update", update, "updateMask", String.join(",", masks))));

    AdResponse after = refresh(context, adGroup, stored.getExternalAdId());
    activityLogService.record(
        companyId,
        stored.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_RESPONSIVE_SEARCH_AD_UPDATED",
        "AD",
        stored.getExternalAdId(),
        before,
        after,
        null);
    return after;
  }

  public AdResponse setStatus(
      Long companyId,
      Long adId,
      String requestedStatus,
      Long actorUserId) {
    Ad stored = requireAd(companyId, adId);
    AdSet adGroup = requireAdGroup(companyId, stored.getAdSetId());
    var context = contextService.requireManaged(companyId, stored.getAdAccountId());
    AdResponse before = AdResponse.from(stored);
    String status = normalizeStatus(requestedStatus);

    Map<String, Object> update =
        Map.of(
            "resourceName",
            apiClient.customerResourceName(
                context.account().getExternalAccountId(),
                "adGroupAds",
                stored.getExternalAdId()),
            "status",
            status);

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "adGroupAds",
        List.of(Map.of("update", update, "updateMask", "status")));

    AdResponse after = refresh(context, adGroup, stored.getExternalAdId());
    activityLogService.record(
        companyId,
        stored.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AD_STATUS_UPDATED",
        "AD",
        stored.getExternalAdId(),
        before,
        after,
        Map.of("requestedStatus", status));
    return after;
  }

  private AdResponse refresh(
      GoogleAdsAccountContextService.Context context,
      AdSet adGroup,
      String externalAdId) {
    JsonNode row =
        syncService.ad(context, adGroup.getExternalAdSetId(), externalAdId);
    return AdResponse.from(
        persistenceService.persistOne(adGroup.getCompanyId(), adGroup.getId(), row));
  }

  private Map<String, Object> rsa(
      List<String> headlines,
      List<String> descriptions,
      String path1,
      String path2) {
    Map<String, Object> rsa = new LinkedHashMap<>();
    rsa.put(
        "headlines",
        headlines.stream().map(value -> Map.of("text", value.trim())).toList());
    rsa.put(
        "descriptions",
        descriptions.stream().map(value -> Map.of("text", value.trim())).toList());
    if (path1 != null && !path1.isBlank()) rsa.put("path1", path1.trim());
    if (path2 != null && !path2.isBlank()) {
      if (path1 == null || path1.isBlank()) {
        throw new BadRequestException("Google RSA path2 requires path1");
      }
      rsa.put("path2", path2.trim());
    }
    return rsa;
  }

  private void validateAssets(List<String> headlines, List<String> descriptions) {
    if (headlines == null || headlines.size() < 3 || headlines.size() > 15) {
      throw new BadRequestException("Google responsive search ads require 3 to 15 headlines");
    }
    if (descriptions == null || descriptions.size() < 2 || descriptions.size() > 4) {
      throw new BadRequestException("Google responsive search ads require 2 to 4 descriptions");
    }
  }

  private void validateUrl(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Google ad final URL is required");
    }
    try {
      java.net.URI uri = java.net.URI.create(value.trim());
      if (!"http".equalsIgnoreCase(uri.getScheme())
          && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException("Google ad final URL must be a valid HTTP or HTTPS URL");
    }
  }

  private List<String> textAssets(JsonNode values) {
    List<String> result = new ArrayList<>();
    if (values != null && values.isArray()) {
      for (JsonNode value : values) {
        String text = nullableText(value, "text");
        if (text != null) result.add(text);
      }
    }
    return result;
  }

  private String nullableText(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private AdSet requireAdGroup(Long companyId, Long adSetId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException("Google ad group is not available for management");
    }
    return adGroup;
  }

  private Ad requireAd(Long companyId, Long adId) {
    Ad ad =
        adRepository
            .findByIdAndCompanyId(adId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad not found"));
    if (ad.getProvider() != AdProvider.GOOGLE_ADS || !ad.isAccessible()) {
      throw new BadRequestException("Google ad is not available for management");
    }
    return ad;
  }

  private String normalizeStatus(String value) {
    String status = value == null ? "" : value.trim().toUpperCase();
    if ("ACTIVE".equals(status)) status = "ENABLED";
    if (!"ENABLED".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException("Google ad status must be ENABLED/ACTIVE or PAUSED");
    }
    return status;
  }

  private String suffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google Ads did not return the created ad");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads ad resource name");
    }
    return resourceName.substring(slash + 1);
  }
}
