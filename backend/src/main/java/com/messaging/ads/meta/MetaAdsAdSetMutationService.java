package com.messaging.ads.meta;

import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaAdSetRequest;
import com.messaging.ads.meta.dto.UpdateMetaAdSetRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsAdSetMutationService {

  private static final Set<String> PUBLISHER_PLATFORMS =
      Set.of("facebook", "instagram", "messenger", "audience_network");

  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final AdConversionRepository conversionRepository;
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsAdSetPersistenceService persistenceService;

  public AdSetResponse create(
      Long companyId, Long campaignId, CreateMetaAdSetRequest request) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    AdAccount account = requireManagedAccount(companyId, campaign.getAdAccountId());
    validateBudgetLocation(campaign, request.dailyBudgetMinor(), request.lifetimeBudgetMinor());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", request.name().trim());
    payload.put("campaign_id", campaign.getExternalCampaignId());
    payload.put("optimization_goal", normalizedRequired(request.optimizationGoal(), "Optimization goal"));
    payload.put("billing_event", normalizedRequired(request.billingEvent(), "Billing event"));
    payload.put("status", "PAUSED");
    payload.put("targeting", targeting(
        request.locations(),
        request.ageMin(),
        request.ageMax(),
        request.genders(),
        request.publisherPlatforms(),
        request.facebookPositions(),
        request.instagramPositions(),
        request.customAudienceIds(),
        request.excludedCustomAudienceIds()));

    putNormalized(payload, "bid_strategy", request.bidStrategy());
    putNormalized(payload, "destination_type", request.destinationType());
    put(payload, "bid_amount", request.bidAmountMinor());
    put(payload, "daily_budget", request.dailyBudgetMinor());
    put(payload, "lifetime_budget", request.lifetimeBudgetMinor());
    put(payload, "start_time", request.startAt());
    put(payload, "end_time", request.endAt());

    Map<String, Object> promotedObject =
        promotedObject(
            companyId,
            account.getId(),
            request.pixelId(),
            request.customEventType(),
            request.customConversionId());
    if (!promotedObject.isEmpty()) payload.put("promoted_object", promotedObject);

    String accessToken = accessToken(companyId, account);
    String externalAdSetId =
        apiClient.createAdSet(accessToken, account.getExternalAccountId(), payload);
    return refresh(companyId, campaign, externalAdSetId, accessToken);
  }

  public AdSetResponse update(
      Long companyId, Long adSetId, UpdateMetaAdSetRequest request) {
    AdSet adSet = requireAdSet(companyId, adSetId);
    AdCampaign campaign = requireCampaign(companyId, adSet.getCampaignId());
    AdAccount account = requireManagedAccount(companyId, adSet.getAdAccountId());

    Map<String, Object> updates = new LinkedHashMap<>();
    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Ad set name cannot be blank");
      updates.put("name", request.name().trim());
    }
    putNormalized(updates, "optimization_goal", request.optimizationGoal());
    putNormalized(updates, "billing_event", request.billingEvent());
    putNormalized(updates, "bid_strategy", request.bidStrategy());
    putNormalized(updates, "destination_type", request.destinationType());
    put(updates, "bid_amount", request.bidAmountMinor());
    put(updates, "daily_budget", request.dailyBudgetMinor());
    put(updates, "lifetime_budget", request.lifetimeBudgetMinor());
    put(updates, "start_time", request.startAt());
    put(updates, "end_time", request.endAt());

    if (hasTargetingChanges(request)) {
      if (request.locations() == null || request.locations().isEmpty()) {
        throw new BadRequestException(
            "Locations are required when replacing Meta ad-set targeting");
      }
      updates.put("targeting", targeting(
          request.locations(),
          request.ageMin(),
          request.ageMax(),
          request.genders(),
          request.publisherPlatforms(),
          request.facebookPositions(),
          request.instagramPositions(),
          request.customAudienceIds(),
          request.excludedCustomAudienceIds()));
    }

    if (request.pixelId() != null
        || request.customEventType() != null
        || request.customConversionId() != null) {
      Map<String, Object> promotedObject =
          promotedObject(
              companyId,
              account.getId(),
              request.pixelId(),
              request.customEventType(),
              request.customConversionId());
      if (!promotedObject.isEmpty()) updates.put("promoted_object", promotedObject);
    }

    if (updates.isEmpty()) throw new BadRequestException("No ad-set changes were supplied");
    validateUpdateBudget(
        campaign,
        adSet,
        request.dailyBudgetMinor(),
        request.lifetimeBudgetMinor());

    String accessToken = accessToken(companyId, account);
    apiClient.updateAdSet(accessToken, adSet.getExternalAdSetId(), updates);
    return refresh(companyId, campaign, adSet.getExternalAdSetId(), accessToken);
  }

  public AdSetResponse setStatus(Long companyId, Long adSetId, String requestedStatus) {
    AdSet adSet = requireAdSet(companyId, adSetId);
    AdCampaign campaign = requireCampaign(companyId, adSet.getCampaignId());
    AdAccount account = requireManagedAccount(companyId, adSet.getAdAccountId());
    String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
    if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException("Ad set status must be ACTIVE or PAUSED");
    }
    String accessToken = accessToken(companyId, account);
    apiClient.updateAdSet(accessToken, adSet.getExternalAdSetId(), Map.of("status", status));
    return refresh(companyId, campaign, adSet.getExternalAdSetId(), accessToken);
  }

  private AdSetResponse refresh(
      Long companyId, AdCampaign campaign, String externalAdSetId, String accessToken) {
    var snapshot = apiClient.adSet(accessToken, externalAdSetId);
    return AdSetResponse.from(
        persistenceService.persistOne(companyId, campaign.getId(), snapshot));
  }

  private Map<String, Object> targeting(
      List<CreateMetaAdSetRequest.TargetingLocation> locations,
      Integer ageMin,
      Integer ageMax,
      List<Integer> genders,
      List<String> publisherPlatforms,
      List<String> facebookPositions,
      List<String> instagramPositions,
      List<String> customAudienceIds,
      List<String> excludedCustomAudienceIds) {
    if (locations == null || locations.isEmpty()) {
      throw new BadRequestException("At least one targeting location is required");
    }

    LinkedHashSet<String> countries = new LinkedHashSet<>();
    LinkedHashSet<String> locationTypes = new LinkedHashSet<>();
    for (CreateMetaAdSetRequest.TargetingLocation location : locations) {
      countries.add(location.countryCode().trim().toUpperCase());
      if (location.locationTypes() != null) {
        location.locationTypes().stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toLowerCase())
            .forEach(locationTypes::add);
      }
    }
    if (locationTypes.isEmpty()) {
      locationTypes.add("home");
      locationTypes.add("recent");
    }

    Map<String, Object> targeting = new LinkedHashMap<>();
    targeting.put(
        "geo_locations",
        Map.of(
            "countries", List.copyOf(countries),
            "location_types", List.copyOf(locationTypes)));
    targeting.put("age_min", ageMin == null ? 18 : ageMin);
    targeting.put("age_max", ageMax == null ? 65 : ageMax);

    if (genders != null && !genders.isEmpty()) {
      if (genders.stream().anyMatch(value -> value == null || (value != 1 && value != 2))) {
        throw new BadRequestException("Meta genders must contain only 1 or 2");
      }
      targeting.put("genders", genders.stream().distinct().toList());
    }

    List<String> normalizedPlatforms = normalizePlatforms(publisherPlatforms);
    if (!normalizedPlatforms.isEmpty()) {
      targeting.put("publisher_platforms", normalizedPlatforms);
    }
    putStringList(targeting, "facebook_positions", facebookPositions, true);
    putStringList(targeting, "instagram_positions", instagramPositions, true);
    putAudienceList(targeting, "custom_audiences", customAudienceIds);
    putAudienceList(targeting, "excluded_custom_audiences", excludedCustomAudienceIds);
    return targeting;
  }

  private List<String> normalizePlatforms(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    List<String> result =
        values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toLowerCase())
            .distinct()
            .toList();
    if (result.stream().anyMatch(value -> !PUBLISHER_PLATFORMS.contains(value))) {
      throw new BadRequestException("Unsupported Meta publisher platform");
    }
    return result;
  }

  private void putStringList(
      Map<String, Object> target, String key, List<String> values, boolean lowercase) {
    if (values == null || values.isEmpty()) return;
    List<String> normalized =
        values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> lowercase ? value.trim().toLowerCase() : value.trim())
            .distinct()
            .toList();
    if (!normalized.isEmpty()) target.put(key, normalized);
  }

  private void putAudienceList(
      Map<String, Object> target, String key, List<String> ids) {
    if (ids == null || ids.isEmpty()) return;
    List<Map<String, String>> audiences = new ArrayList<>();
    ids.stream()
        .filter(id -> id != null && !id.isBlank())
        .map(String::trim)
        .distinct()
        .forEach(id -> audiences.add(Map.of("id", id)));
    if (!audiences.isEmpty()) target.put(key, audiences);
  }

  private Map<String, Object> promotedObject(
      Long companyId,
      Long accountId,
      String pixelId,
      String customEventType,
      String customConversionId) {
    Map<String, Object> promotedObject = new LinkedHashMap<>();

    if (customConversionId != null && !customConversionId.isBlank()) {
      AdConversion conversion = requireConversion(companyId, accountId, customConversionId);
      promotedObject.put("custom_conversion_id", conversion.getExternalConversionId());
      if (conversion.getPixelId() != null && !conversion.getPixelId().isBlank()) {
        promotedObject.put("pixel_id", conversion.getPixelId());
      }
      if (conversion.getCustomEventType() != null && !conversion.getCustomEventType().isBlank()) {
        promotedObject.put("custom_event_type", conversion.getCustomEventType());
      }
      return promotedObject;
    }

    if (pixelId != null && !pixelId.isBlank()) {
      promotedObject.put("pixel_id", pixelId.trim());
    }
    if (customEventType != null && !customEventType.isBlank()) {
      if (!promotedObject.containsKey("pixel_id")) {
        throw new BadRequestException("A Meta pixel is required for a custom conversion event");
      }
      promotedObject.put("custom_event_type", customEventType.trim().toUpperCase());
    }
    return promotedObject;
  }

  private AdConversion requireConversion(
      Long companyId, Long accountId, String conversionId) {
    Long internalId;
    try {
      internalId = Long.valueOf(conversionId);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Custom conversion ID is invalid");
    }
    AdConversion conversion =
        conversionRepository
            .findByIdAndCompanyId(internalId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising custom conversion not found"));
    if (conversion.getProvider() != AdProvider.META_ADS
        || !accountId.equals(conversion.getAdAccountId())
        || !conversion.isAccessible()
        || conversion.isArchived()
        || conversion.isUnavailable()) {
      throw new BadRequestException(
          "Custom conversion is not available for this Meta ad account");
    }
    return conversion;
  }

  private boolean hasTargetingChanges(UpdateMetaAdSetRequest request) {
    return request.locations() != null
        || request.ageMin() != null
        || request.ageMax() != null
        || request.genders() != null
        || request.publisherPlatforms() != null
        || request.facebookPositions() != null
        || request.instagramPositions() != null
        || request.customAudienceIds() != null
        || request.excludedCustomAudienceIds() != null;
  }

  private void validateBudgetLocation(
      AdCampaign campaign, Long dailyBudgetMinor, Long lifetimeBudgetMinor) {
    boolean campaignHasBudget =
        campaign.getDailyBudgetMinor() != null || campaign.getLifetimeBudgetMinor() != null;
    if (campaignHasBudget && (dailyBudgetMinor != null || lifetimeBudgetMinor != null)) {
      throw new BadRequestException(
          "This campaign uses a campaign-level budget, so omit the ad-set budget");
    }
    if (!campaignHasBudget && dailyBudgetMinor == null && lifetimeBudgetMinor == null) {
      throw new BadRequestException(
          "This campaign has no campaign-level budget, so the ad set requires a budget");
    }
  }

  private void validateUpdateBudget(
      AdCampaign campaign,
      AdSet adSet,
      Long dailyBudgetMinor,
      Long lifetimeBudgetMinor) {
    boolean campaignHasBudget =
        campaign.getDailyBudgetMinor() != null || campaign.getLifetimeBudgetMinor() != null;
    boolean adSetAlreadyHasBudget =
        adSet.getDailyBudgetMinor() != null || adSet.getLifetimeBudgetMinor() != null;
    boolean requestProvidesBudget = dailyBudgetMinor != null || lifetimeBudgetMinor != null;
    if (campaignHasBudget && requestProvidesBudget) {
      throw new BadRequestException(
          "This campaign uses a campaign-level budget, so omit the ad-set budget");
    }
    if (!campaignHasBudget && !adSetAlreadyHasBudget && !requestProvidesBudget) {
      throw new BadRequestException(
          "This campaign has no campaign-level budget, so the ad set requires a budget");
    }
  }

  private AdCampaign requireCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.META_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Meta campaign is not available for ad-set management");
    }
    return campaign;
  }

  private AdSet requireAdSet(Long companyId, Long adSetId) {
    AdSet adSet =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad set not found"));
    if (adSet.getProvider() != AdProvider.META_ADS || !adSet.isAccessible()) {
      throw new BadRequestException("Meta ad set is not available for management");
    }
    return adSet;
  }

  private AdAccount requireManagedAccount(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }
    return account;
  }

  private String accessToken(Long companyId, AdAccount account) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    return tokenService.read(connection.getId()).token().accessToken();
  }

  private String normalizedRequired(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(label + " is required");
    }
    return value.trim().toUpperCase();
  }

  private void putNormalized(Map<String, Object> target, String key, String value) {
    if (value != null && !value.isBlank()) target.put(key, value.trim().toUpperCase());
  }

  private void put(Map<String, Object> target, String key, Long value) {
    if (value != null) target.put(key, value);
  }

  private void put(Map<String, Object> target, String key, Instant value) {
    if (value != null) target.put(key, value.toString());
  }
}
