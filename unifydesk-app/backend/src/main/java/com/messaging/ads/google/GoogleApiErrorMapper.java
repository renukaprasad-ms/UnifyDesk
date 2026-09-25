package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

final class GoogleApiErrorMapper {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private GoogleApiErrorMapper() {}

  static String googleAds(RestClientResponseException exception, String fallback) {
    String body = exception.getResponseBodyAsString();
    String normalized = body == null ? "" : body.toUpperCase();

    if (normalized.contains("NOT_ADS_USER")) {
      return "The connected Google account is not linked to a Google Ads account. "
          + "Use a Google account with Ads access, then reconnect.";
    }
    if (normalized.contains("CLOUD_PROJECT_NOT_APPROVED_FOR_PRODUCTION")) {
      return "This Google Cloud project is not approved for production Google Ads accounts. "
          + "Upgrade the Google Ads API access level and try again.";
    }
    if (normalized.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
        || normalized.contains("INSUFFICIENT_AUTHENTICATION_SCOPES")
        || normalized.contains("INSUFFICIENT_SCOPE")) {
      return "Google Ads permission is missing. Reconnect Google Ads and grant the requested permissions.";
    }
    if (normalized.contains("AUTHENTICATION_ERROR")
        || exception.getStatusCode().value() == 401) {
      return "Google Ads authorization is no longer valid. Reconnect Google Ads and try again.";
    }
    if (normalized.contains("SERVICE_DISABLED")) {
      return "Google Ads API is not enabled for this Google Cloud project. Enable it in Google Cloud Console and retry.";
    }
    if (normalized.contains("UNRECOGNIZED_FIELD")) {
      return "Google Ads rejected an API field used by this integration. "
          + "The configured Google Ads API version and integration fields need to be updated.";
    }
    if (normalized.contains("CONTAINS_EU_POLITICAL_ADVERTISING")
        && normalized.contains("REQUIRED")) {
      return "Choose whether this campaign contains EU political advertising and try again.";
    }
    if (exception.getStatusCode().value() == 429) {
      return "Google Ads rate limit reached. Wait a short time and try again.";
    }
    if (exception.getStatusCode().value() == 403) {
      return "Google Ads denied this request. Check the connected account permissions and Google Ads API access level.";
    }

    String providerMessage = firstGoogleAdsMessage(body);
    if (providerMessage != null) {
      return fallback + ": " + providerMessage;
    }
    return fallback + ". Google Ads returned HTTP " + exception.getStatusCode().value() + ".";
  }

  static String merchant(RestClientResponseException exception, String fallback) {
    String body = exception.getResponseBodyAsString();
    String normalized = body == null ? "" : body.toUpperCase();

    if (normalized.contains("SERVICE_DISABLED")
        || normalized.contains("MERCHANTAPI.GOOGLEAPIS.COM")) {
      return "Merchant API is not enabled for this Google Cloud project. "
          + "Enable Merchant API in Google Cloud Console, wait a few minutes, then retry.";
    }
    if (normalized.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
        || normalized.contains("INSUFFICIENT_SCOPE")
        || exception.getStatusCode().value() == 401) {
      return "Merchant Center permission is missing or expired. "
          + "Reconnect Google Ads with Merchant Center permission and try again.";
    }
    if (exception.getStatusCode().value() == 403) {
      return "Google denied Merchant Center access. "
          + "Check that the connected Google account has access to the Merchant Center account.";
    }
    if (exception.getStatusCode().value() == 429) {
      return "Google Merchant API rate limit reached. Wait a short time and try again.";
    }

    String providerMessage = topLevelMessage(body);
    if (providerMessage != null) {
      return fallback + ": " + providerMessage;
    }
    return fallback + ". Google Merchant API returned HTTP " + exception.getStatusCode().value() + ".";
  }

  static String dataManager(RestClientResponseException exception, String fallback) {
    String body = exception.getResponseBodyAsString();
    String normalized = body == null ? "" : body.toUpperCase();

    if (normalized.contains("SERVICE_DISABLED")
        || normalized.contains("DATAMANAGER.GOOGLEAPIS.COM")) {
      return "Google Data Manager API is not enabled for this Google Cloud project. "
          + "Enable Data Manager API in Google Cloud Console and retry.";
    }
    if (normalized.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
        || normalized.contains("INSUFFICIENT_SCOPE")
        || exception.getStatusCode().value() == 401) {
      return "Google Data Manager permission is missing or expired. "
          + "Reconnect Google Ads with the required Data Manager permission.";
    }
    if (exception.getStatusCode().value() == 429) {
      return "Google Data Manager rate limit reached. Wait a short time and try again.";
    }

    String providerMessage = topLevelMessage(body);
    if (providerMessage != null) {
      return fallback + ": " + providerMessage;
    }
    return fallback + ". Google Data Manager returned HTTP " + exception.getStatusCode().value() + ".";
  }

  private static String firstGoogleAdsMessage(String body) {
    JsonNode root = parse(body);
    if (root == null) return null;
    JsonNode details = root.path("error").path("details");
    if (details.isArray()) {
      for (JsonNode detail : details) {
        JsonNode errors = detail.path("errors");
        if (!errors.isArray()) continue;
        for (JsonNode error : errors) {
          String message = clean(error.path("message").asText(null));
          if (message != null) return message;
        }
      }
    }
    return clean(root.path("error").path("message").asText(null));
  }

  private static String topLevelMessage(String body) {
    JsonNode root = parse(body);
    if (root == null) return null;
    return clean(root.path("error").path("message").asText(null));
  }

  private static JsonNode parse(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      return JSON_MAPPER.readTree(body);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String clean(String message) {
    if (message == null || message.isBlank()) return null;
    String cleaned = message.replaceAll("\\s+", " ").trim();
    return cleaned.length() > 280 ? cleaned.substring(0, 277) + "..." : cleaned;
  }
}
