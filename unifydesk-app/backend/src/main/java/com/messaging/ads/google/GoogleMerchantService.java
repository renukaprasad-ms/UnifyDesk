package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleMerchantDataSourceRequest;
import com.messaging.ads.google.dto.DeleteGoogleMerchantProductRequest;
import com.messaging.ads.google.dto.GoogleMerchantAccountResponse;
import com.messaging.ads.google.dto.GoogleMerchantDataSourceResponse;
import com.messaging.ads.google.dto.GoogleMerchantProductPage;
import com.messaging.ads.google.dto.GoogleMerchantProductResponse;
import com.messaging.ads.google.dto.UpsertGoogleMerchantProductRequest;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleMerchantService {

  private static final Pattern MERCHANT_ID = Pattern.compile("^\\d+$");
  private static final Pattern DATA_SOURCE_NAME =
      Pattern.compile("^accounts/(\\d+)/dataSources/(\\d+)$");
  private static final Set<String> AVAILABILITY =
      Set.of("IN_STOCK", "OUT_OF_STOCK", "PREORDER", "BACKORDER");
  private static final Set<String> CONDITION = Set.of("NEW", "REFURBISHED", "USED");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleMerchantApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public List<GoogleMerchantAccountResponse> accounts(Long companyId, Long adAccountId) {
    var context = contextService.requireManaged(companyId, adAccountId);
    List<GoogleMerchantAccountResponse> result = new ArrayList<>();
    String pageToken = null;

    do {
      JsonNode response = apiClient.listAccounts(context.accessToken(), pageToken);
      JsonNode accounts = response.path("accounts");
      if (accounts.isArray()) {
        for (JsonNode account : accounts) {
          result.add(mapAccount(account));
        }
      }
      pageToken = text(response, "nextPageToken");
    } while (pageToken != null);

    return List.copyOf(result);
  }

  public List<GoogleMerchantDataSourceResponse> dataSources(
      Long companyId, Long adAccountId, String merchantId) {
    var context = merchantContext(companyId, adAccountId, merchantId);
    List<GoogleMerchantDataSourceResponse> result = new ArrayList<>();
    String pageToken = null;

    do {
      JsonNode response =
          apiClient.listDataSources(context.google().accessToken(), context.merchantId(), pageToken);
      JsonNode sources = response.path("dataSources");
      if (sources.isArray()) {
        for (JsonNode source : sources) {
          result.add(mapDataSource(source));
        }
      }
      pageToken = text(response, "nextPageToken");
    } while (pageToken != null);

    return List.copyOf(result);
  }

  public GoogleMerchantDataSourceResponse createDataSource(
      Long companyId,
      Long adAccountId,
      String merchantId,
      CreateGoogleMerchantDataSourceRequest request,
      Long actorUserId) {
    var context = merchantContext(companyId, adAccountId, merchantId);

    String language = request.contentLanguage().trim().toLowerCase(Locale.ROOT);
    if (!language.matches("[a-z]{2,3}")) {
      throw new BadRequestException(
          "Merchant content language must be a two or three letter language code");
    }

    String feedLabel = request.feedLabel().trim().toUpperCase(Locale.ROOT);
    if (!feedLabel.matches("[A-Z0-9-]{1,20}")) {
      throw new BadRequestException(
          "Merchant feed label must use up to 20 uppercase letters, numbers, or dashes");
    }

    List<String> countries =
        request.countries().stream()
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .filter(value -> value.matches("[A-Z]{2}"))
            .distinct()
            .toList();
    if (countries.isEmpty()) {
      throw new BadRequestException("Merchant data source requires at least one country");
    }

    Map<String, Object> primary =
        Map.of(
            "contentLanguage", language,
            "feedLabel", feedLabel,
            "countries", countries);

    JsonNode created =
        apiClient.createDataSource(
            context.google().accessToken(),
            context.merchantId(),
            Map.of(
                "displayName", request.displayName().trim(),
                "primaryProductDataSource", primary));

    GoogleMerchantDataSourceResponse response = mapDataSource(created);
    activityLogService.record(
        companyId,
        adAccountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_MERCHANT_DATA_SOURCE_CREATED",
        "MERCHANT_DATA_SOURCE",
        response.dataSourceId(),
        null,
        response,
        Map.of("merchantId", context.merchantId()));

    return response;
  }

  public void deleteDataSource(
      Long companyId,
      Long adAccountId,
      String merchantId,
      String dataSourceId,
      Long actorUserId) {
    var context = merchantContext(companyId, adAccountId, merchantId);
    String id = numeric(dataSourceId, "Merchant data source ID");

    GoogleMerchantDataSourceResponse existing =
        requireDataSource(companyId, adAccountId, context.merchantId(), id, true);

    apiClient.deleteDataSource(
        context.google().accessToken(), context.merchantId(), id);

    activityLogService.record(
        companyId,
        adAccountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_MERCHANT_DATA_SOURCE_DELETED",
        "MERCHANT_DATA_SOURCE",
        id,
        existing,
        null,
        Map.of("merchantId", context.merchantId()));
  }

  public GoogleMerchantProductPage products(
      Long companyId,
      Long adAccountId,
      String merchantId,
      String pageToken,
      int pageSize) {
    var context = merchantContext(companyId, adAccountId, merchantId);
    JsonNode response =
        apiClient.listProducts(
            context.google().accessToken(),
            context.merchantId(),
            blank(pageToken),
            pageSize);

    List<GoogleMerchantProductResponse> products = new ArrayList<>();
    JsonNode values = response.path("products");
    if (values.isArray()) {
      for (JsonNode product : values) products.add(mapProduct(product));
    }

    return new GoogleMerchantProductPage(
        List.copyOf(products), text(response, "nextPageToken"));
  }

  public GoogleMerchantProductResponse upsertProduct(
      Long companyId,
      Long adAccountId,
      String merchantId,
      UpsertGoogleMerchantProductRequest request,
      Long actorUserId) {
    var context = merchantContext(companyId, adAccountId, merchantId);
    String dataSource = requireDataSourceResource(context.merchantId(), request.dataSourceName());
    requireDataSource(
        companyId,
        adAccountId,
        context.merchantId(),
        suffix(dataSource),
        true);

    String language = request.contentLanguage().trim().toLowerCase(Locale.ROOT);
    String feedLabel = request.feedLabel().trim().toUpperCase(Locale.ROOT);
    String offerId = request.offerId().trim();
    if (!language.matches("[a-z]{2,3}")) {
      throw new BadRequestException("Product content language is invalid");
    }
    if (!feedLabel.matches("[A-Z0-9-]{1,20}")) {
      throw new BadRequestException("Product feed label is invalid");
    }
    if (offerId.length() > 240) {
      throw new BadRequestException("Product offer ID is too long");
    }

    validateUrl(request.link(), "Product landing-page URL");
    validateUrl(request.imageLink(), "Product image URL");

    String availability = request.availability().trim().toUpperCase(Locale.ROOT);
    String condition = request.condition().trim().toUpperCase(Locale.ROOT);
    if (!AVAILABILITY.contains(availability)) {
      throw new BadRequestException(
          "Product availability must be IN_STOCK, OUT_OF_STOCK, PREORDER, or BACKORDER");
    }
    if (!CONDITION.contains(condition)) {
      throw new BadRequestException(
          "Product condition must be NEW, REFURBISHED, or USED");
    }

    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("title", request.title().trim());
    putText(attributes, "description", request.description());
    attributes.put("link", request.link().trim());
    attributes.put("imageLink", request.imageLink().trim());
    attributes.put("availability", availability);
    attributes.put("condition", condition);
    putText(attributes, "brand", request.brand());
    putText(attributes, "googleProductCategory", request.googleProductCategory());

    if (request.price() != null) {
      String currency = blank(request.currency());
      if (currency == null || !currency.matches("[A-Za-z]{3}")) {
        throw new BadRequestException(
            "Product currency must be a three-letter ISO currency code when price is supplied");
      }
      BigDecimal micros =
          request.price().movePointRight(6).setScale(0, RoundingMode.HALF_UP);
      attributes.put(
          "price",
          Map.of(
              "amountMicros", micros.toPlainString(),
              "currencyCode", currency.toUpperCase(Locale.ROOT)));
    }

    if (request.gtins() != null) {
      List<String> gtins =
          request.gtins().stream()
              .filter(value -> value != null && !value.isBlank())
              .map(String::trim)
              .filter(value -> value.matches("\\d{8,14}"))
              .distinct()
              .toList();
      if (!gtins.isEmpty()) attributes.put("gtins", gtins);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("offerId", offerId);
    body.put("contentLanguage", language);
    body.put("feedLabel", feedLabel);
    body.put("productAttributes", attributes);

    JsonNode productInput =
        apiClient.insertProductInput(
            context.google().accessToken(),
            context.merchantId(),
            dataSource,
            body);

    GoogleMerchantProductResponse response =
        new GoogleMerchantProductResponse(
            text(productInput, "product"),
            offerId,
            language,
            feedLabel,
            dataSource,
            request.title().trim(),
            blank(request.description()),
            request.link().trim(),
            request.imageLink().trim(),
            availability,
            condition,
            blank(request.brand()),
            request.price() == null
                ? null
                : request
                    .price()
                    .movePointRight(6)
                    .setScale(0, RoundingMode.HALF_UP)
                    .toPlainString(),
            request.price() == null || blank(request.currency()) == null
                ? null
                : request.currency().trim().toUpperCase(Locale.ROOT),
            request.gtins() == null ? List.of() : List.copyOf(request.gtins()),
            blank(request.googleProductCategory()),
            List.of());

    activityLogService.record(
        companyId,
        adAccountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_MERCHANT_PRODUCT_UPSERTED",
        "MERCHANT_PRODUCT",
        language + "~" + feedLabel + "~" + offerId,
        null,
        response,
        Map.of(
            "merchantId", context.merchantId(),
            "dataSource", dataSource));

    return response;
  }

  public void deleteProduct(
      Long companyId,
      Long adAccountId,
      String merchantId,
      DeleteGoogleMerchantProductRequest request,
      Long actorUserId) {
    var context = merchantContext(companyId, adAccountId, merchantId);
    String dataSource = requireDataSourceResource(context.merchantId(), request.dataSourceName());
    requireDataSource(
        companyId,
        adAccountId,
        context.merchantId(),
        suffix(dataSource),
        true);

    String rawProductId =
        request.contentLanguage().trim().toLowerCase(Locale.ROOT)
            + "~"
            + request.feedLabel().trim().toUpperCase(Locale.ROOT)
            + "~"
            + request.offerId().trim();
    String encodedId =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rawProductId.getBytes(StandardCharsets.UTF_8));

    apiClient.deleteProductInput(
        context.google().accessToken(),
        context.merchantId(),
        encodedId,
        dataSource);

    activityLogService.record(
        companyId,
        adAccountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_MERCHANT_PRODUCT_DELETED",
        "MERCHANT_PRODUCT",
        rawProductId,
        null,
        null,
        Map.of(
            "merchantId", context.merchantId(),
            "dataSource", dataSource));
  }

  private MerchantContext merchantContext(
      Long companyId, Long adAccountId, String merchantId) {
    var google = contextService.requireManaged(companyId, adAccountId);
    String id = numeric(merchantId, "Merchant Center account ID");

    boolean accessible =
        accounts(companyId, adAccountId).stream()
            .anyMatch(account -> id.equals(account.accountId()));
    if (!accessible) {
      throw new BadRequestException(
          "Merchant Center account is not accessible to the connected Google user");
    }

    return new MerchantContext(google, id);
  }

  private GoogleMerchantDataSourceResponse requireDataSource(
      Long companyId,
      Long adAccountId,
      String merchantId,
      String dataSourceId,
      boolean requireApi) {
    GoogleMerchantDataSourceResponse source =
        dataSources(companyId, adAccountId, merchantId).stream()
            .filter(value -> dataSourceId.equals(value.dataSourceId()))
            .findFirst()
            .orElseThrow(
                () -> new BadRequestException(
                    "Merchant Center data source is not accessible"));

    if (requireApi && !source.apiManaged()) {
      throw new BadRequestException(
          "Reachlyo can mutate products only in Merchant Center API data sources");
    }
    return source;
  }

  private GoogleMerchantAccountResponse mapAccount(JsonNode account) {
    String name = text(account, "name");
    return new GoogleMerchantAccountResponse(
        name,
        suffix(name),
        text(account, "accountName"),
        text(account, "timeZone"),
        text(account, "languageCode"),
        account.path("adultContent").asBoolean(false));
  }

  private GoogleMerchantDataSourceResponse mapDataSource(JsonNode source) {
    JsonNode primary = source.path("primaryProductDataSource");
    String input = text(source, "input");
    return new GoogleMerchantDataSourceResponse(
        text(source, "name"),
        first(text(source, "dataSourceId"), suffix(text(source, "name"))),
        text(source, "displayName"),
        input,
        text(primary, "contentLanguage"),
        text(primary, "feedLabel"),
        strings(primary.path("countries")),
        "API".equalsIgnoreCase(input));
  }

  private GoogleMerchantProductResponse mapProduct(JsonNode product) {
    JsonNode attributes = product.path("productAttributes");
    JsonNode price = attributes.path("price");
    List<String> issues = new ArrayList<>();
    JsonNode issueNodes = product.path("productStatus").path("itemLevelIssues");
    if (issueNodes.isArray()) {
      for (JsonNode issue : issueNodes) {
        String code = first(text(issue, "code"), "UNKNOWN");
        String severity = first(text(issue, "severity"), "UNKNOWN");
        issues.add(severity + ": " + code);
      }
    }

    return new GoogleMerchantProductResponse(
        text(product, "name"),
        text(product, "offerId"),
        text(product, "contentLanguage"),
        text(product, "feedLabel"),
        text(product, "dataSource"),
        text(attributes, "title"),
        text(attributes, "description"),
        text(attributes, "link"),
        text(attributes, "imageLink"),
        text(attributes, "availability"),
        text(attributes, "condition"),
        text(attributes, "brand"),
        text(price, "amountMicros"),
        text(price, "currencyCode"),
        strings(attributes.path("gtins")),
        text(attributes, "googleProductCategory"),
        List.copyOf(issues));
  }

  private String requireDataSourceResource(String merchantId, String resource) {
    if (resource == null) {
      throw new BadRequestException("Merchant data source is required");
    }
    var matcher = DATA_SOURCE_NAME.matcher(resource.trim());
    if (!matcher.matches() || !merchantId.equals(matcher.group(1))) {
      throw new BadRequestException(
          "Merchant data source does not belong to the selected Merchant Center account");
    }
    return resource.trim();
  }

  private String numeric(String value, String label) {
    if (value == null || !MERCHANT_ID.matcher(value.trim()).matches()) {
      throw new BadRequestException(label + " must contain digits only");
    }
    return value.trim();
  }

  private void validateUrl(String value, String label) {
    try {
      URI uri = URI.create(value.trim());
      if (!"http".equalsIgnoreCase(uri.getScheme())
          && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException(label + " must be a valid HTTP or HTTPS URL");
    }
  }

  private void putText(Map<String, Object> target, String key, String value) {
    String normalized = blank(value);
    if (normalized != null) target.put(key, normalized);
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText().trim();
  }

  private String suffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) return "";
    int slash = resourceName.lastIndexOf('/');
    return slash < 0 ? resourceName : resourceName.substring(slash + 1);
  }

  private String first(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    LinkedHashSet<String> values = new LinkedHashSet<>();
    node.forEach(value -> {
      if (!value.asText().isBlank()) values.add(value.asText());
    });
    return List.copyOf(values);
  }

  private record MerchantContext(
      GoogleAdsAccountContextService.Context google,
      String merchantId) {}
}
