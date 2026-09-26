package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAssetLinkRequest;
import com.messaging.ads.google.dto.GoogleAdvancedReusableAssetCreateRequest;
import com.messaging.ads.google.dto.GooglePriceOfferingInput;
import com.messaging.ads.google.dto.GoogleReusableAssetCreateRequest;
import com.messaging.ads.google.dto.GoogleReusableAssetLinkResponse;
import com.messaging.ads.google.dto.GoogleReusableAssetResponse;
import com.messaging.ads.google.dto.GoogleUrlCustomParameter;
import com.messaging.ads.google.dto.GoogleUrlOptionsRequest;
import com.messaging.ads.google.dto.GoogleUrlOptionsResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsReusableAssetService {

  private static final Set<String> REUSABLE_TYPES =
      Set.of(
          "SITELINK",
          "CALLOUT",
          "STRUCTURED_SNIPPET",
          "CALL",
          "PRICE",
          "PROMOTION",
          "LEAD_FORM",
          "TEXT",
          "IMAGE");

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

  private static final Set<String> PRICE_TYPES =
      Set.of(
          "BRANDS",
          "EVENTS",
          "LOCATIONS",
          "NEIGHBORHOODS",
          "PRODUCT_CATEGORIES",
          "PRODUCT_TIERS",
          "SERVICES",
          "SERVICE_CATEGORIES",
          "SERVICE_TIERS");
  private static final Set<String> PRICE_QUALIFIERS =
      Set.of("FROM", "UP_TO", "AVERAGE");
  private static final Set<String> PRICE_UNITS =
      Set.of("PER_HOUR", "PER_DAY", "PER_WEEK", "PER_MONTH", "PER_YEAR", "PER_NIGHT");
  private static final Set<String> LEAD_CTA_TYPES =
      Set.of(
          "LEARN_MORE",
          "GET_QUOTE",
          "APPLY_NOW",
          "SIGN_UP",
          "CONTACT_US",
          "SUBSCRIBE",
          "DOWNLOAD",
          "BOOK_NOW",
          "GET_OFFER",
          "REGISTER",
          "GET_INFO",
          "REQUEST_DEMO",
          "JOIN_NOW",
          "GET_STARTED");
  private static final Set<String> LEAD_POST_CTA_TYPES =
      Set.of("VISIT_SITE", "DOWNLOAD", "LEARN_MORE", "SHOP_NOW");
  private static final Set<String> LEAD_FIELDS =
      Set.of(
          "FULL_NAME",
          "FIRST_NAME",
          "LAST_NAME",
          "EMAIL",
          "PHONE_NUMBER",
          "POSTAL_CODE",
          "STREET_ADDRESS",
          "CITY",
          "REGION",
          "COUNTRY",
          "WORK_EMAIL",
          "COMPANY_NAME",
          "WORK_PHONE",
          "JOB_TITLE");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final AdActivityLogService activityLogService;

  public List<GoogleReusableAssetResponse> assets(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    String query =
        "SELECT asset.id, asset.resource_name, asset.name, asset.type, asset.source, "
            + "asset.sitelink_asset.link_text, asset.sitelink_asset.description1, "
            + "asset.sitelink_asset.description2, asset.callout_asset.callout_text, "
            + "asset.structured_snippet_asset.header, asset.structured_snippet_asset.values, "
            + "asset.text_asset.text, asset.image_asset.full_size.url, "
            + "asset.image_asset.full_size.width_pixels, asset.image_asset.full_size.height_pixels, "
            + "asset.final_urls, asset.final_mobile_urls, asset.tracking_url_template, "
            + "asset.final_url_suffix, asset.url_custom_parameters "
            + "FROM asset WHERE asset.type IN "
            + "('SITELINK','CALLOUT','STRUCTURED_SNIPPET','CALL','PRICE','PROMOTION','LEAD_FORM','TEXT','IMAGE') "
            + "AND asset.source = 'ADVERTISER'";
    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(this::asset)
        .toList();
  }

  public GoogleReusableAssetResponse create(
      Long companyId,
      Long accountId,
      GoogleReusableAssetCreateRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google reusable assets must be created in a managed advertising customer account");
    }

    Map<String, Object> create = buildAsset(request);
    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "assets",
            List.of(Map.of("create", create)));
    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google did not return the created asset resource");
    }
    String assetId = suffix(resourceName, "asset");

    GoogleReusableAssetResponse after =
        assets(companyId, accountId).stream()
            .filter(item -> assetId.equals(item.id()))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Google asset was created but could not be reloaded"));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_REUSABLE_ASSET_CREATED",
        "ASSET",
        assetId,
        null,
        after,
        Map.of("type", request.type().name()));
    return after;
  }

  public GoogleReusableAssetResponse createAdvanced(
      Long companyId,
      Long accountId,
      GoogleAdvancedReusableAssetCreateRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google reusable assets must be created in a managed advertising customer account");
    }

    Map<String, Object> create = buildAdvancedAsset(request);
    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "assets",
            List.of(Map.of("create", create)));
    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google did not return the created asset resource");
    }
    String assetId = suffix(resourceName, "asset");

    GoogleReusableAssetResponse after =
        assets(companyId, accountId).stream()
            .filter(item -> assetId.equals(item.id()))
            .findFirst()
            .orElse(
                new GoogleReusableAssetResponse(
                    assetId,
                    resourceName,
                    hasText(request.name()) ? request.name().trim() : null,
                    request.type().name(),
                    "ADVERTISER",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    List.of()));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_REUSABLE_ASSET_CREATED",
        "ASSET",
        assetId,
        null,
        after,
        Map.of("type", request.type().name()));
    return after;
  }

  public List<GoogleReusableAssetLinkResponse> links(
      Long companyId,
      Long accountId,
      GoogleAssetLinkRequest.Level level,
      Long targetId) {
    var context = contextService.requireManaged(companyId, accountId);
    LinkTarget target = linkTarget(companyId, accountId, context, level, targetId);
    String query = linkQuery(target);
    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(row -> link(row, target))
        .toList();
  }

  public GoogleReusableAssetLinkResponse attach(
      Long companyId,
      Long accountId,
      String assetId,
      GoogleAssetLinkRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String normalizedAssetId = numeric(assetId, "asset");
    GoogleReusableAssetResponse asset =
        assets(companyId, accountId).stream()
            .filter(item -> normalizedAssetId.equals(item.id()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Google reusable asset not found"));

    if (!REUSABLE_TYPES.contains(asset.type())) {
      throw new BadRequestException("Unsupported reusable Google asset type: " + asset.type());
    }

    String fieldType = resolveFieldType(asset, request);
    LinkTarget target =
        linkTarget(companyId, accountId, context, request.level(), request.targetId());
    if ("LEAD_FORM".equals(fieldType)
        && request.level() != GoogleAssetLinkRequest.Level.CAMPAIGN) {
      throw new BadRequestException(
          "Google Lead Form assets can only be attached at campaign level");
    }

    var existing =
        links(companyId, accountId, request.level(), request.targetId()).stream()
            .filter(
                item ->
                    normalizedAssetId.equals(item.assetId())
                        && fieldType.equals(item.fieldType()))
            .findFirst();
    if (existing.isPresent()) return existing.get();

    Map<String, Object> create = new LinkedHashMap<>();
    create.put(target.parentField(), target.parentResourceName());
    create.put(
        "asset",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(), "assets", normalizedAssetId));
    create.put("fieldType", fieldType);

    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            target.collection(),
            List.of(Map.of("create", create)));
    String linkResource = apiClient.firstMutatedResourceName(response);
    if (linkResource == null || linkResource.isBlank()) {
      throw new BadRequestException("Google did not return the created asset link");
    }

    GoogleReusableAssetLinkResponse after =
        links(companyId, accountId, request.level(), request.targetId()).stream()
            .filter(item -> linkResource.equals(item.resourceName()))
            .findFirst()
            .orElse(
                new GoogleReusableAssetLinkResponse(
                    linkResource,
                    asset.resourceName(),
                    asset.id(),
                    asset.name(),
                    asset.type(),
                    fieldType,
                    request.level().name(),
                    request.targetId() == null ? null : request.targetId().toString(),
                    "ENABLED",
                    null));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_REUSABLE_ASSET_ATTACHED",
        request.level().name(),
        target.auditTargetId(),
        null,
        after,
        Map.of(
            "assetId", normalizedAssetId,
            "fieldType", fieldType,
            "level", request.level().name()));
    return after;
  }

  public void detach(
      Long companyId,
      Long accountId,
      String assetId,
      GoogleAssetLinkRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String normalizedAssetId = numeric(assetId, "asset");
    LinkTarget target =
        linkTarget(companyId, accountId, context, request.level(), request.targetId());
    String requestedFieldType =
        request.fieldType() == null
            ? null
            : request.fieldType().trim().toUpperCase(Locale.ROOT);
    GoogleReusableAssetLinkResponse existing =
        links(companyId, accountId, request.level(), request.targetId()).stream()
            .filter(
                item ->
                    normalizedAssetId.equals(item.assetId())
                        && (requestedFieldType == null
                            || requestedFieldType.equals(item.fieldType())))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Google asset is not attached at the requested level"));

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        target.collection(),
        List.of(Map.of("remove", existing.resourceName())));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_REUSABLE_ASSET_DETACHED",
        request.level().name(),
        target.auditTargetId(),
        existing,
        null,
        Map.of(
            "assetId", normalizedAssetId,
            "fieldType", existing.fieldType(),
            "level", request.level().name()));
  }

  public GoogleUrlOptionsResponse urlOptions(
      Long companyId,
      Long accountId,
      GoogleAssetLinkRequest.Level level,
      Long targetId) {
    var context = contextService.requireManaged(companyId, accountId);
    UrlTarget target = urlTarget(companyId, accountId, context, level, targetId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            target.query());
    if (rows.isEmpty()) {
      throw new NotFoundException("Google URL options target could not be loaded");
    }
    JsonNode node = rows.getFirst().path(target.jsonField());
    return new GoogleUrlOptionsResponse(
        level.name(),
        target.auditTargetId(),
        text(node, "trackingUrlTemplate"),
        text(node, "finalUrlSuffix"),
        level == GoogleAssetLinkRequest.Level.CUSTOMER
            ? List.of()
            : customParameters(node.path("urlCustomParameters")));
  }

  public GoogleUrlOptionsResponse updateUrlOptions(
      Long companyId,
      Long accountId,
      GoogleAssetLinkRequest.Level level,
      Long targetId,
      GoogleUrlOptionsRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    UrlTarget target = urlTarget(companyId, accountId, context, level, targetId);
    GoogleUrlOptionsResponse before = urlOptions(companyId, accountId, level, targetId);

    Map<String, Object> update = new LinkedHashMap<>();
    update.put("resourceName", target.resourceName());
    List<String> masks = new ArrayList<>();

    if (request.trackingUrlTemplate() != null) {
      update.put("trackingUrlTemplate", request.trackingUrlTemplate().trim());
      masks.add("tracking_url_template");
    }
    if (request.finalUrlSuffix() != null) {
      update.put("finalUrlSuffix", request.finalUrlSuffix().trim());
      masks.add("final_url_suffix");
    }
    if (request.customParameters() != null) {
      if (level == GoogleAssetLinkRequest.Level.CUSTOMER
          && !request.customParameters().isEmpty()) {
        throw new BadRequestException(
            "Google customer-level URL options do not support custom parameters");
      }
      if (level != GoogleAssetLinkRequest.Level.CUSTOMER) {
        List<GoogleUrlCustomParameter> params =
            validateCustomParameters(request.customParameters());
        update.put(
            "urlCustomParameters",
            params.stream()
                .map(item -> Map.of("key", item.key(), "value", item.value()))
                .toList());
        masks.add("url_custom_parameters");
      }
    }

    if (masks.isEmpty()) {
      throw new BadRequestException("No Google URL option changes were supplied");
    }

    Map<String, Object> operation =
        Map.of(
            "update", update,
            "updateMask", String.join(",", masks));
    if (level == GoogleAssetLinkRequest.Level.CUSTOMER) {
      apiClient.mutateCustomer(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          operation);
    } else {
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          target.collection(),
          List.of(operation));
    }

    GoogleUrlOptionsResponse after = urlOptions(companyId, accountId, level, targetId);
    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_URL_OPTIONS_UPDATED",
        level.name(),
        target.auditTargetId(),
        before,
        after,
        Map.of("updatedFields", masks));
    return after;
  }

  private Map<String, Object> buildAsset(GoogleReusableAssetCreateRequest request) {
    if (request.type() == null) {
      throw new BadRequestException("Google reusable asset type is required");
    }

    Map<String, Object> asset = new LinkedHashMap<>();
    if (hasText(request.name())) asset.put("name", request.name().trim());

    switch (request.type()) {
      case SITELINK -> {
        requireText(request.linkText(), "Sitelink link text");
        requireText(request.finalUrl(), "Sitelink final URL");
        validateHttpUrl(request.finalUrl(), "Sitelink final URL");
        if (hasText(request.finalMobileUrl())) {
          validateHttpUrl(request.finalMobileUrl(), "Sitelink mobile URL");
        }
        boolean description1 = hasText(request.description1());
        boolean description2 = hasText(request.description2());
        if (description1 != description2) {
          throw new BadRequestException(
              "Sitelink description1 and description2 must be supplied together");
        }
        Map<String, Object> sitelink = new LinkedHashMap<>();
        sitelink.put("linkText", request.linkText().trim());
        if (description1) {
          sitelink.put("description1", request.description1().trim());
          sitelink.put("description2", request.description2().trim());
        }
        asset.put("sitelinkAsset", sitelink);
        asset.put("finalUrls", List.of(request.finalUrl().trim()));
        if (hasText(request.finalMobileUrl())) {
          asset.put("finalMobileUrls", List.of(request.finalMobileUrl().trim()));
        }
        applyAssetUrlOptions(asset, request);
      }
      case CALLOUT -> {
        requireText(request.calloutText(), "Callout text");
        asset.put("calloutAsset", Map.of("calloutText", request.calloutText().trim()));
      }
      case STRUCTURED_SNIPPET -> {
        requireText(request.structuredSnippetHeader(), "Structured snippet header");
        List<String> values =
            normalizeStrings(request.structuredSnippetValues(), 3, 10, "Structured snippet values");
        asset.put(
            "structuredSnippetAsset",
            Map.of(
                "header", request.structuredSnippetHeader().trim(),
                "values", values));
      }
      case BUSINESS_NAME -> {
        validateText(request.text(), 25, "Business name");
        asset.put("textAsset", Map.of("text", request.text().trim()));
      }
      case AD_IMAGE -> {
        byte[] data = decodeImage(request.imageBase64());
        validateImage(data, ImageRule.AD_IMAGE);
        asset.put("imageAsset", Map.of("data", Base64.getEncoder().encodeToString(data)));
      }
      case BUSINESS_LOGO -> {
        byte[] data = decodeImage(request.imageBase64());
        validateImage(data, ImageRule.BUSINESS_LOGO);
        asset.put("imageAsset", Map.of("data", Base64.getEncoder().encodeToString(data)));
      }
    }
    return asset;
  }

  private Map<String, Object> buildAdvancedAsset(
      GoogleAdvancedReusableAssetCreateRequest request) {
    if (request.type() == null) {
      throw new BadRequestException("Google advanced asset type is required");
    }
    Map<String, Object> asset = new LinkedHashMap<>();
    if (hasText(request.name())) asset.put("name", request.name().trim());

    switch (request.type()) {
      case CALL -> {
        requireText(request.callCountryCode(), "Call asset country code");
        requireText(request.callPhoneNumber(), "Call asset phone number");
        String countryCode = request.callCountryCode().trim().toUpperCase(Locale.ROOT);
        if (!countryCode.matches("[A-Z]{2}")) {
          throw new BadRequestException(
              "Call asset country code must be a two-letter country code");
        }
        asset.put(
            "callAsset",
            Map.of(
                "countryCode", countryCode,
                "phoneNumber", request.callPhoneNumber().trim(),
                "callConversionReportingState",
                "USE_ACCOUNT_LEVEL_CALL_CONVERSION_ACTION"));
      }
      case PRICE -> {
        String type = enumValue(request.priceType(), PRICE_TYPES, "Price asset type", true);
        String qualifier =
            enumValue(request.priceQualifier(), PRICE_QUALIFIERS, "Price qualifier", false);
        requireText(request.languageCode(), "Price asset language code");
        if (request.priceOfferings() == null
            || request.priceOfferings().size() < 3
            || request.priceOfferings().size() > 8) {
          throw new BadRequestException(
              "Google Price assets require between 3 and 8 offerings");
        }
        List<Map<String, Object>> offerings = new ArrayList<>();
        for (GooglePriceOfferingInput offering : request.priceOfferings()) {
          if (offering == null) {
            throw new BadRequestException("Google Price offering cannot be empty");
          }
          requireText(offering.header(), "Price offering header");
          requireText(offering.description(), "Price offering description");
          requireText(offering.currencyCode(), "Price offering currency");
          requireText(offering.finalUrl(), "Price offering final URL");
          if (offering.amount() == null || offering.amount().signum() <= 0) {
            throw new BadRequestException("Price offering amount must be greater than zero");
          }
          validateHttpUrl(offering.finalUrl(), "Price offering final URL");
          if (hasText(offering.finalMobileUrl())) {
            validateHttpUrl(offering.finalMobileUrl(), "Price offering mobile URL");
          }
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("header", offering.header().trim());
          item.put("description", offering.description().trim());
          item.put(
              "price",
              Map.of(
                  "amountMicros", majorToMicros(offering.amount()),
                  "currencyCode",
                  offering.currencyCode().trim().toUpperCase(Locale.ROOT)));
          if (hasText(offering.unit())) {
            item.put(
                "unit",
                enumValue(offering.unit(), PRICE_UNITS, "Price offering unit", true));
          }
          item.put("finalUrl", offering.finalUrl().trim());
          if (hasText(offering.finalMobileUrl())) {
            item.put("finalMobileUrl", offering.finalMobileUrl().trim());
          }
          offerings.add(item);
        }
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("type", type);
        price.put("languageCode", request.languageCode().trim());
        price.put("priceOfferings", offerings);
        if (qualifier != null) price.put("priceQualifier", qualifier);
        asset.put("priceAsset", price);
      }
      case PROMOTION -> {
        requireText(request.promotionTarget(), "Promotion target");
        requireText(request.finalUrl(), "Promotion final URL");
        validateHttpUrl(request.finalUrl(), "Promotion final URL");
        boolean percent = request.promotionPercentOff() != null;
        boolean money = request.promotionMoneyAmountOff() != null;
        if (percent == money) {
          throw new BadRequestException(
              "Promotion requires exactly one discount: percent off or money amount off");
        }
        Map<String, Object> promotion = new LinkedHashMap<>();
        promotion.put("promotionTarget", request.promotionTarget().trim());
        if (percent) {
          if (request.promotionPercentOff().signum() <= 0
              || request.promotionPercentOff().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException(
                "Promotion percent off must be greater than 0 and at most 100");
          }
          promotion.put(
              "percentOff",
              request
                  .promotionPercentOff()
                  .multiply(BigDecimal.valueOf(10_000L))
                  .setScale(0, RoundingMode.HALF_UP)
                  .longValueExact());
        } else {
          requireText(request.promotionCurrencyCode(), "Promotion currency code");
          if (request.promotionMoneyAmountOff().signum() <= 0) {
            throw new BadRequestException(
                "Promotion money amount off must be greater than zero");
          }
          promotion.put(
              "moneyAmountOff",
              Map.of(
                  "amountMicros", majorToMicros(request.promotionMoneyAmountOff()),
                  "currencyCode",
                  request.promotionCurrencyCode().trim().toUpperCase(Locale.ROOT)));
        }
        if (hasText(request.promotionDiscountModifier())) {
          promotion.put(
              "discountModifier",
              enumValue(
                  request.promotionDiscountModifier(),
                  Set.of("UP_TO"),
                  "Promotion discount modifier",
                  true));
        }
        if (hasText(request.promotionCode())) {
          promotion.put("promotionCode", request.promotionCode().trim());
        }
        if (hasText(request.promotionOccasion())) {
          promotion.put(
              "occasion", request.promotionOccasion().trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(request.promotionLanguageCode())) {
          promotion.put("languageCode", request.promotionLanguageCode().trim());
        }
        asset.put("promotionAsset", promotion);
        asset.put("finalUrls", List.of(request.finalUrl().trim()));
      }
      case LEAD_FORM -> {
        requireText(request.leadBusinessName(), "Lead Form business name");
        requireText(request.leadCallToActionType(), "Lead Form call-to-action");
        requireText(
            request.leadCallToActionDescription(),
            "Lead Form call-to-action description");
        requireText(request.leadHeadline(), "Lead Form headline");
        requireText(request.leadDescription(), "Lead Form description");
        requireText(request.leadPrivacyPolicyUrl(), "Lead Form privacy policy URL");
        requireText(request.finalUrl(), "Lead Form final URL");
        validateHttpUrl(request.leadPrivacyPolicyUrl(), "Lead Form privacy policy URL");
        validateHttpUrl(request.finalUrl(), "Lead Form final URL");
        String cta =
            enumValue(
                request.leadCallToActionType(),
                LEAD_CTA_TYPES,
                "Lead Form call-to-action",
                true);
        List<String> fields =
            normalizeEnumValues(
                request.leadFields(), LEAD_FIELDS, "Lead Form fields", 1, 20);
        Map<String, Object> lead = new LinkedHashMap<>();
        lead.put("businessName", request.leadBusinessName().trim());
        lead.put("callToActionType", cta);
        lead.put(
            "callToActionDescription",
            request.leadCallToActionDescription().trim());
        lead.put("headline", request.leadHeadline().trim());
        lead.put("description", request.leadDescription().trim());
        lead.put("privacyPolicyUrl", request.leadPrivacyPolicyUrl().trim());
        lead.put(
            "fields",
            fields.stream().map(value -> Map.of("inputType", value)).toList());
        if (hasText(request.leadPostSubmitCallToActionType())) {
          lead.put(
              "postSubmitCallToActionType",
              enumValue(
                  request.leadPostSubmitCallToActionType(),
                  LEAD_POST_CTA_TYPES,
                  "Lead Form post-submit call-to-action",
                  true));
        }
        if (hasText(request.leadPostSubmitHeadline())) {
          lead.put("postSubmitHeadline", request.leadPostSubmitHeadline().trim());
        }
        if (hasText(request.leadPostSubmitDescription())) {
          lead.put(
              "postSubmitDescription",
              request.leadPostSubmitDescription().trim());
        }
        asset.put("leadFormAsset", lead);
        asset.put("finalUrls", List.of(request.finalUrl().trim()));
      }
    }
    return asset;
  }

  private String enumValue(
      String value, Set<String> allowed, String label, boolean required) {
    if (!hasText(value)) {
      if (required) throw new BadRequestException(label + " is required");
      return null;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new BadRequestException(
          "Unsupported Google " + label.toLowerCase(Locale.ROOT) + ": " + normalized);
    }
    return normalized;
  }

  private List<String> normalizeEnumValues(
      List<String> values,
      Set<String> allowed,
      String label,
      int min,
      int max) {
    if (values == null) throw new BadRequestException(label + " are required");
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      result.add(enumValue(value, allowed, label, true));
    }
    if (result.size() < min || result.size() > max) {
      throw new BadRequestException(
          label + " must contain between " + min + " and " + max + " values");
    }
    if (result.contains("FULL_NAME")
        && (result.contains("FIRST_NAME") || result.contains("LAST_NAME"))) {
      throw new BadRequestException(
          "Lead Form FULL_NAME cannot be combined with FIRST_NAME or LAST_NAME");
    }
    return List.copyOf(result);
  }

  private long majorToMicros(BigDecimal value) {
    try {
      return value
          .multiply(BigDecimal.valueOf(1_000_000L))
          .setScale(0, RoundingMode.HALF_UP)
          .longValueExact();
    } catch (ArithmeticException exception) {
      throw new BadRequestException("Google asset money value is too large");
    }
  }

  private void applyAssetUrlOptions(
      Map<String, Object> asset, GoogleReusableAssetCreateRequest request) {
    if (hasText(request.trackingUrlTemplate())) {
      asset.put("trackingUrlTemplate", request.trackingUrlTemplate().trim());
    }
    if (hasText(request.finalUrlSuffix())) {
      asset.put("finalUrlSuffix", request.finalUrlSuffix().trim());
    }
    if (request.customParameters() != null) {
      List<GoogleUrlCustomParameter> params =
          validateCustomParameters(request.customParameters());
      if (!params.isEmpty()) {
        asset.put(
            "urlCustomParameters",
            params.stream()
                .map(item -> Map.of("key", item.key(), "value", item.value()))
                .toList());
      }
    }
  }

  private LinkTarget linkTarget(
      Long companyId,
      Long accountId,
      GoogleAdsAccountContextService.Context context,
      GoogleAssetLinkRequest.Level level,
      Long targetId) {
    if (level == null) throw new BadRequestException("Google asset link level is required");
    String customerId = context.account().getExternalAccountId();
    return switch (level) {
      case CUSTOMER -> {
        if (targetId != null) {
          throw new BadRequestException("Customer asset links do not accept targetId");
        }
        yield new LinkTarget(
            "customerAssets",
            "customer",
            "customers/" + customerId,
            "customerAsset",
            null,
            accountId.toString());
      }
      case CAMPAIGN -> {
        AdCampaign campaign = requireCampaign(companyId, accountId, targetId);
        yield new LinkTarget(
            "campaignAssets",
            "campaign",
            apiClient.customerResourceName(
                customerId, "campaigns", campaign.getExternalCampaignId()),
            "campaignAsset",
            campaign.getExternalCampaignId(),
            campaign.getId().toString());
      }
      case AD_GROUP -> {
        AdSet adGroup = requireAdGroup(companyId, accountId, targetId);
        yield new LinkTarget(
            "adGroupAssets",
            "adGroup",
            apiClient.customerResourceName(
                customerId, "adGroups", adGroup.getExternalAdSetId()),
            "adGroupAsset",
            adGroup.getExternalAdSetId(),
            adGroup.getId().toString());
      }
    };
  }

  private String linkQuery(LinkTarget target) {
    String prefix = target.jsonField();
    String gaqlPrefix = snake(prefix);
    StringBuilder query =
        new StringBuilder(
            "SELECT "
                + gaqlPrefix
                + ".resource_name, "
                + gaqlPrefix
                + ".asset, "
                + gaqlPrefix
                + ".field_type, "
                + gaqlPrefix
                + ".status, "
                + gaqlPrefix
                + ".primary_status, "
                + "asset.id, asset.resource_name, asset.name, asset.type FROM "
                + gaqlPrefix);
    if (target.externalTargetId() != null) {
      query
          .append(" WHERE ")
          .append(snake(target.parentField()))
          .append(".id = ")
          .append(numeric(target.externalTargetId(), target.parentField()));
      query.append(" AND ");
    } else {
      query.append(" WHERE ");
    }
    query
        .append(gaqlPrefix)
        .append(".field_type IN ('SITELINK','CALLOUT','STRUCTURED_SNIPPET','CALL','PRICE','PROMOTION','LEAD_FORM','AD_IMAGE','BUSINESS_NAME','BUSINESS_LOGO')");
    return query.toString();
  }

  private GoogleReusableAssetLinkResponse link(JsonNode row, LinkTarget target) {
    JsonNode link = row.path(target.jsonField());
    JsonNode asset = row.path("asset");
    return new GoogleReusableAssetLinkResponse(
        text(link, "resourceName"),
        text(link, "asset"),
        text(asset, "id"),
        text(asset, "name"),
        text(asset, "type"),
        text(link, "fieldType"),
        targetLevel(target),
        target.auditTargetId(),
        text(link, "status"),
        text(link, "primaryStatus"));
  }

  private String targetLevel(LinkTarget target) {
    return switch (target.collection()) {
      case "customerAssets" -> GoogleAssetLinkRequest.Level.CUSTOMER.name();
      case "campaignAssets" -> GoogleAssetLinkRequest.Level.CAMPAIGN.name();
      default -> GoogleAssetLinkRequest.Level.AD_GROUP.name();
    };
  }

  private UrlTarget urlTarget(
      Long companyId,
      Long accountId,
      GoogleAdsAccountContextService.Context context,
      GoogleAssetLinkRequest.Level level,
      Long targetId) {
    if (level == null) throw new BadRequestException("Google URL option level is required");
    String customerId = context.account().getExternalAccountId();
    return switch (level) {
      case CUSTOMER -> {
        if (targetId != null) {
          throw new BadRequestException("Customer URL options do not accept targetId");
        }
        yield new UrlTarget(
            "customers",
            "customers/" + customerId,
            "customer",
            "SELECT customer.id, customer.tracking_url_template, "
                + "customer.final_url_suffix FROM customer LIMIT 1",
            accountId.toString());
      }
      case CAMPAIGN -> {
        AdCampaign campaign = requireCampaign(companyId, accountId, targetId);
        yield new UrlTarget(
            "campaigns",
            apiClient.customerResourceName(
                customerId, "campaigns", campaign.getExternalCampaignId()),
            "campaign",
            "SELECT campaign.id, campaign.tracking_url_template, "
                + "campaign.final_url_suffix, campaign.url_custom_parameters "
                + "FROM campaign WHERE campaign.id = "
                + numeric(campaign.getExternalCampaignId(), "campaign"),
            campaign.getId().toString());
      }
      case AD_GROUP -> {
        AdSet adGroup = requireAdGroup(companyId, accountId, targetId);
        yield new UrlTarget(
            "adGroups",
            apiClient.customerResourceName(
                customerId, "adGroups", adGroup.getExternalAdSetId()),
            "adGroup",
            "SELECT ad_group.id, ad_group.tracking_url_template, "
                + "ad_group.final_url_suffix, ad_group.url_custom_parameters "
                + "FROM ad_group WHERE ad_group.id = "
                + numeric(adGroup.getExternalAdSetId(), "ad group"),
            adGroup.getId().toString());
      }
    };
  }

  private AdCampaign requireCampaign(Long companyId, Long accountId, Long campaignId) {
    if (campaignId == null) {
      throw new BadRequestException("Campaign targetId is required");
    }
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !accountId.equals(campaign.getAdAccountId())) {
      throw new BadRequestException("Google campaign is not available for this account");
    }
    return campaign;
  }

  private AdSet requireAdGroup(Long companyId, Long accountId, Long adGroupId) {
    if (adGroupId == null) {
      throw new BadRequestException("Ad group targetId is required");
    }
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adGroupId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS
        || !adGroup.isAccessible()
        || !accountId.equals(adGroup.getAdAccountId())) {
      throw new BadRequestException("Google ad group is not available for this account");
    }
    return adGroup;
  }

  private GoogleReusableAssetResponse asset(JsonNode row) {
    JsonNode asset = row.path("asset");
    JsonNode sitelink = asset.path("sitelinkAsset");
    JsonNode callout = asset.path("calloutAsset");
    JsonNode snippet = asset.path("structuredSnippetAsset");
    JsonNode image = asset.path("imageAsset").path("fullSize");
    return new GoogleReusableAssetResponse(
        text(asset, "id"),
        text(asset, "resourceName"),
        text(asset, "name"),
        text(asset, "type"),
        text(asset, "source"),
        text(sitelink, "linkText"),
        text(sitelink, "description1"),
        text(sitelink, "description2"),
        text(callout, "calloutText"),
        text(snippet, "header"),
        strings(snippet.path("values")),
        strings(asset.path("finalUrls")),
        strings(asset.path("finalMobileUrls")),
        text(asset, "trackingUrlTemplate"),
        text(asset, "finalUrlSuffix"),
        customParameters(asset.path("urlCustomParameters")),
        text(asset.path("textAsset"), "text"),
        text(image, "url"),
        longOrNull(image, "widthPixels"),
        longOrNull(image, "heightPixels"));
  }

  private String resolveFieldType(
      GoogleReusableAssetResponse asset, GoogleAssetLinkRequest request) {
    String requested =
        request.fieldType() == null ? null : request.fieldType().trim().toUpperCase(Locale.ROOT);
    if ("TEXT".equals(asset.type())) {
      if (!"BUSINESS_NAME".equals(requested)) {
        throw new BadRequestException(
            "Google text identity assets must be linked with fieldType BUSINESS_NAME");
      }
      if (request.level() == GoogleAssetLinkRequest.Level.AD_GROUP) {
        throw new BadRequestException(
            "Google Business Name identity assets are not supported at ad-group level");
      }
      return requested;
    }
    if ("IMAGE".equals(asset.type())) {
      if (!"AD_IMAGE".equals(requested) && !"BUSINESS_LOGO".equals(requested)) {
        throw new BadRequestException(
            "Google image assets require fieldType AD_IMAGE or BUSINESS_LOGO");
      }
      if ("BUSINESS_LOGO".equals(requested)
          && request.level() != GoogleAssetLinkRequest.Level.CAMPAIGN) {
        throw new BadRequestException(
            "Google Business Logo identity assets can only be attached at campaign level");
      }
      if ("AD_IMAGE".equals(requested)
          && request.level() == GoogleAssetLinkRequest.Level.CUSTOMER) {
        throw new BadRequestException(
            "Google AD_IMAGE assets must be linked at campaign or ad-group level");
      }
      return requested;
    }
    if (requested != null && !requested.equals(asset.type())) {
      throw new BadRequestException(
          "Google asset fieldType must match the reusable asset type");
    }
    return asset.type();
  }

  private byte[] decodeImage(String encoded) {
    if (!hasText(encoded)) {
      throw new BadRequestException("Google image asset data is required");
    }
    String value = encoded.trim();
    int comma = value.indexOf(',');
    if (value.startsWith("data:") && comma >= 0) value = value.substring(comma + 1);
    try {
      byte[] data = Base64.getDecoder().decode(value);
      if (data.length == 0 || data.length > MAX_IMAGE_BYTES) {
        throw new BadRequestException(
            "Google image assets must be non-empty and at most 5 MB");
      }
      return data;
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Google image asset data is not valid base64");
    }
  }

  private void validateImage(byte[] data, ImageRule rule) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      if (image == null) {
        throw new BadRequestException("Google image asset must be PNG, JPEG, or GIF");
      }
      if (image.getWidth() < rule.minWidth || image.getHeight() < rule.minHeight) {
        throw new BadRequestException(
            rule.label + " must be at least " + rule.minWidth + "x" + rule.minHeight + " pixels");
      }
      if (rule.ratio > 0) {
        double ratio = (double) image.getWidth() / (double) image.getHeight();
        if (Math.abs(ratio - rule.ratio) > rule.tolerance) {
          throw new BadRequestException(rule.label + " has an invalid aspect ratio");
        }
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read Google image asset");
    }
  }

  private void validateText(String value, int max, String label) {
    requireText(value, label);
    if (value.trim().length() > max) {
      throw new BadRequestException(label + " must be at most " + max + " characters");
    }
  }

  private Long longOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isIntegralNumber()) return value.longValue();
    try {
      return new BigDecimal(value.asText()).longValue();
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private List<GoogleUrlCustomParameter> validateCustomParameters(
      List<GoogleUrlCustomParameter> values) {
    if (values == null) return List.of();
    if (values.size() > 8) {
      throw new BadRequestException("Google URL options support at most 8 custom parameters");
    }
    Set<String> keys = new LinkedHashSet<>();
    List<GoogleUrlCustomParameter> result = new ArrayList<>();
    for (GoogleUrlCustomParameter item : values) {
      if (item == null || !hasText(item.key()) || !hasText(item.value())) {
        throw new BadRequestException("Google custom parameter key and value are required");
      }
      String key = item.key().trim();
      String value = item.value().trim();
      if (!key.matches("[A-Za-z0-9]+")) {
        throw new BadRequestException(
            "Google custom parameter keys may contain only letters and numbers");
      }
      if (key.getBytes(StandardCharsets.UTF_8).length > 16) {
        throw new BadRequestException(
            "Google custom parameter keys may contain at most 16 bytes");
      }
      if (value.getBytes(StandardCharsets.UTF_8).length > 200) {
        throw new BadRequestException(
            "Google custom parameter values may contain at most 200 bytes");
      }
      if (!keys.add(key.toLowerCase(Locale.ROOT))) {
        throw new BadRequestException(
            "Google custom parameter keys must be unique ignoring case");
      }
      result.add(new GoogleUrlCustomParameter(key, value));
    }
    return List.copyOf(result);
  }

  private List<GoogleUrlCustomParameter> customParameters(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<GoogleUrlCustomParameter> result = new ArrayList<>();
    for (JsonNode item : node) {
      String key = text(item, "key");
      String value = text(item, "value");
      if (key != null && value != null) {
        result.add(new GoogleUrlCustomParameter(key, value));
      }
    }
    return List.copyOf(result);
  }

  private List<String> normalizeStrings(
      List<String> values, int min, int max, String label) {
    if (values == null) {
      throw new BadRequestException(label + " are required");
    }
    List<String> normalized =
        values.stream()
            .filter(this::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    if (normalized.size() < min || normalized.size() > max) {
      throw new BadRequestException(
          label + " must contain between " + min + " and " + max + " values");
    }
    return normalized;
  }

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    node.forEach(
        item -> {
          if (item.isTextual() && !item.asText().isBlank()) {
            result.add(item.asText().trim());
          }
        });
    return List.copyOf(result);
  }

  private void validateHttpUrl(String value, String label) {
    try {
      URI uri = URI.create(value.trim());
      if (uri.getScheme() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null) {
        throw new BadRequestException(label + " must be an absolute HTTP(S) URL");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(label + " must be a valid absolute HTTP(S) URL");
    }
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google " + label + " ID");
    }
    return value.trim();
  }

  private String suffix(String resourceName, String label) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google did not return a " + label + " resource name");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google " + label + " resource name");
    }
    return resourceName.substring(slash + 1);
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String text = value.asText(null);
    return hasText(text) ? text.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void requireText(String value, String label) {
    if (!hasText(value)) throw new BadRequestException(label + " is required");
  }

  private String snake(String value) {
    StringBuilder result = new StringBuilder();
    for (char ch : value.toCharArray()) {
      if (Character.isUpperCase(ch)) {
        result.append('_').append(Character.toLowerCase(ch));
      } else {
        result.append(ch);
      }
    }
    return result.toString();
  }

  private enum ImageRule {
    AD_IMAGE("Ad image", 300, 300, 0d, 0d),
    BUSINESS_LOGO("Business logo", 128, 128, 1.0d, 0.08d);

    final String label;
    final int minWidth;
    final int minHeight;
    final double ratio;
    final double tolerance;

    ImageRule(String label, int minWidth, int minHeight, double ratio, double tolerance) {
      this.label = label;
      this.minWidth = minWidth;
      this.minHeight = minHeight;
      this.ratio = ratio;
      this.tolerance = tolerance;
    }
  }

  private record LinkTarget(
      String collection,
      String parentField,
      String parentResourceName,
      String jsonField,
      String externalTargetId,
      String auditTargetId) {}

  private record UrlTarget(
      String collection,
      String resourceName,
      String jsonField,
      String query,
      String auditTargetId) {}
}
