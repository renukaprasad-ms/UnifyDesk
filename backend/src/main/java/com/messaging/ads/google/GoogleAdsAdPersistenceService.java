package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAdSyncResponse;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsAdPersistenceService {

  private final AdSetRepository adSetRepository;
  private final AdRepository adRepository;
  private final AdCreativeRepository creativeRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleAdSyncResponse persist(
      Long companyId, Long adSetId, List<JsonNode> rows) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);

    Map<String, Ad> existing = new HashMap<>();
    adRepository
        .findByCompanyIdAndAdSetIdOrderByProviderUpdatedAtDesc(companyId, adSetId)
        .stream()
        .filter(ad -> ad.getProvider() == AdProvider.GOOGLE_ADS)
        .forEach(ad -> existing.put(ad.getExternalAdId(), ad));

    Instant syncedAt = Instant.now();
    List<Ad> changed = new ArrayList<>();
    int count = 0;

    for (JsonNode row : rows) {
      String externalAdId = externalAdId(row.path("adGroupAd"));
      if (externalAdId == null) continue;
      Ad ad = existing.remove(externalAdId);
      changed.add(apply(adGroup, ad, row, syncedAt));
      count++;
    }

    existing.values().forEach(
        ad -> {
          ad.setAccessible(false);
          ad.setLastSyncAt(syncedAt);
          changed.add(ad);
          if (ad.getExternalCreativeId() != null) {
            creativeRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalCreativeId(
                    companyId,
                    ad.getAdAccountId(),
                    AdProvider.GOOGLE_ADS,
                    ad.getExternalCreativeId())
                .ifPresent(
                    creative -> {
                      creative.setAccessible(false);
                      creative.setLastSyncAt(syncedAt);
                      creativeRepository.save(creative);
                    });
          }
        });

    adRepository.saveAll(changed);
    return new GoogleAdSyncResponse(count);
  }

  @Transactional
  public Ad persistOne(Long companyId, Long adSetId, JsonNode row) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    String externalAdId = externalAdId(row.path("adGroupAd"));
    if (externalAdId == null) {
      throw new BadRequestException("Google ad snapshot is missing an ID");
    }

    Ad existing =
        adRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdId(
                companyId,
                adGroup.getAdAccountId(),
                AdProvider.GOOGLE_ADS,
                externalAdId)
            .orElse(null);
    return adRepository.save(apply(adGroup, existing, row, Instant.now()));
  }

  private Ad apply(AdSet adGroup, Ad ad, JsonNode row, Instant syncedAt) {
    JsonNode adGroupAd = row.path("adGroupAd");
    JsonNode snapshot = adGroupAd.path("ad");
    String externalAdId = externalAdId(adGroupAd);
    String creativeExternalId = text(snapshot, "id");

    AdCreative creative =
        creativeExternalId == null
            ? null
            : creativeRepository
                .findByCompanyIdAndAdAccountIdAndProviderAndExternalCreativeId(
                    adGroup.getCompanyId(),
                    adGroup.getAdAccountId(),
                    AdProvider.GOOGLE_ADS,
                    creativeExternalId)
                .orElseGet(AdCreative::new);

    if (creative != null) {
      if (creative.getId() == null) {
        creative.setCompanyId(adGroup.getCompanyId());
        creative.setAdAccountId(adGroup.getAdAccountId());
        creative.setProvider(AdProvider.GOOGLE_ADS);
        creative.setExternalCreativeId(creativeExternalId);
      }
      String adType = first(text(snapshot, "type"), "UNKNOWN");
      creative.setName(
          first(
              text(snapshot, "name"),
              "Google " + adType.replace('_', ' ') + " " + creativeExternalId));
      creative.setCreativeFormat(adType);

      JsonNode formatPayload;
      if ("RESPONSIVE_DISPLAY_AD".equals(adType)) {
        JsonNode display = snapshot.path("responsiveDisplayAd");
        creative.setHeadline(
            first(
                joinTextAssets(display.path("headlines")),
                text(display.path("longHeadline"), "text")));
        creative.setDescription(joinTextAssets(display.path("descriptions")));
        creative.setPrimaryText(text(display, "businessName"));
        formatPayload = display;
      } else if ("DEMAND_GEN_VIDEO_RESPONSIVE_AD".equals(adType)) {
        JsonNode demandGen = snapshot.path("demandGenVideoResponsiveAd");
        creative.setHeadline(
            first(
                joinTextAssets(demandGen.path("headlines")),
                joinTextAssets(demandGen.path("longHeadlines"))));
        creative.setDescription(joinTextAssets(demandGen.path("descriptions")));
        creative.setPrimaryText(text(demandGen.path("businessName"), "text"));
        formatPayload = demandGen;
      } else if ("SHOPPING_PRODUCT_AD".equals(adType)) {
        creative.setHeadline("Shopping product ad");
        creative.setDescription(null);
        formatPayload = snapshot.path("shoppingProductAd");
      } else {
        JsonNode rsa = snapshot.path("responsiveSearchAd");
        creative.setHeadline(joinTextAssets(rsa.path("headlines")));
        creative.setDescription(joinTextAssets(rsa.path("descriptions")));
        formatPayload = rsa;
      }

      creative.setDestinationUrl(firstArrayText(snapshot.path("finalUrls")));
      creative.setUrlTags(text(snapshot, "finalUrlSuffix"));
      creative.setObjectStorySpecJson(json(formatPayload));
      creative.setAccessible(true);
      creative.setProviderPayloadJson(json(snapshot));
      creative.setLastSyncAt(syncedAt);
      creative = creativeRepository.save(creative);
    }

    if (ad == null) {
      ad = new Ad();
      ad.setCompanyId(adGroup.getCompanyId());
      ad.setAdAccountId(adGroup.getAdAccountId());
      ad.setCampaignId(adGroup.getCampaignId());
      ad.setAdSetId(adGroup.getId());
      ad.setProvider(AdProvider.GOOGLE_ADS);
      ad.setExternalAdId(externalAdId);
    }

    ad.setCreativeId(creative == null ? null : creative.getId());
    ad.setExternalCreativeId(creativeExternalId);
    ad.setName(
        first(
            text(snapshot, "name"),
            creative == null ? "Google Ad " + externalAdId : creative.getName()));
    ad.setProviderStatus(text(adGroupAd, "status"));
    ad.setEffectiveStatus(text(adGroupAd, "primaryStatus"));
    ad.setStatus(mapStatus(text(adGroupAd, "status")));
    ad.setTrackingSpecsJson(json(snapshot.path("urlCustomParameters")));
    ad.setConversionSpecsJson(json(adGroupAd.path("policySummary")));
    ad.setAccessible(true);
    ad.setProviderPayloadJson(json(row));
    ad.setLastSyncAt(syncedAt);
    return ad;
  }

  private AdSet requireAdGroup(Long companyId, Long adSetId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException("Google ad group is not available for ad synchronization");
    }
    return adGroup;
  }

  private String externalAdId(JsonNode adGroupAd) {
    String resourceName = text(adGroupAd, "resourceName");
    if (resourceName != null) {
      int slash = resourceName.lastIndexOf('/');
      if (slash >= 0 && slash < resourceName.length() - 1) {
        return resourceName.substring(slash + 1);
      }
    }
    String adId = text(adGroupAd.path("ad"), "id");
    return adId;
  }

  private AdEntityStatus mapStatus(String status) {
    if (status == null) return AdEntityStatus.UNKNOWN;
    return switch (status.toUpperCase()) {
      case "ENABLED" -> AdEntityStatus.ACTIVE;
      case "PAUSED" -> AdEntityStatus.PAUSED;
      case "REMOVED" -> AdEntityStatus.REMOVED;
      default -> AdEntityStatus.UNKNOWN;
    };
  }

  private String joinTextAssets(JsonNode values) {
    if (values == null || !values.isArray()) return null;
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      String text = text(value, "text");
      if (text != null) result.add(text);
    }
    return result.isEmpty() ? null : String.join(" | ", result);
  }

  private String firstArrayText(JsonNode values) {
    if (values == null || !values.isArray() || values.isEmpty()) return null;
    String value = values.get(0).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String json(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google ad snapshot", exception);
    }
  }
}
