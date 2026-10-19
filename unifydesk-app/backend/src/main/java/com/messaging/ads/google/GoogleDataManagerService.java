package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleConversionEvent;
import com.messaging.ads.google.dto.GoogleConversionEventUploadRequest;
import com.messaging.ads.google.dto.GoogleCustomerMatchMember;
import com.messaging.ads.google.dto.GoogleCustomerMatchUploadRequest;
import com.messaging.ads.google.dto.GoogleDataManagerUploadResponse;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleDataManagerService {

  private static final int MAX_AUDIENCE_MEMBERS_PER_REQUEST = 10_000;
  private static final int MAX_EVENTS_PER_REQUEST = 2_000;

  private final GoogleAdsAccountContextService contextService;
  private final GoogleDataManagerApiClient apiClient;
  private final AdAudienceRepository audienceRepository;
  private final AdConversionRepository conversionRepository;
  private final AdActivityLogService activityLogService;

  public GoogleDataManagerUploadResponse uploadAudienceMembers(
      Long companyId, Long audienceId, GoogleCustomerMatchUploadRequest request, Long actorUserId) {
    AdAudience audience =
        audienceRepository
            .findByIdAndCompanyId(audienceId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising audience not found"));

    if (audience.getProvider() != AdProvider.GOOGLE_ADS || !audience.isAccessible()) {
      throw new BadRequestException("Google Customer Match audience is not available");
    }
    if (!request.customerMatchTermsAccepted()) {
      throw new BadRequestException(
          "Customer Match terms must be accepted before uploading audience members");
    }

    var context = contextService.requireManaged(companyId, audience.getAdAccountId());
    List<GoogleCustomerMatchMember> members = request.members();
    if (members == null || members.isEmpty()) {
      throw new BadRequestException("Customer Match upload requires at least one member");
    }

    List<Map<String, Object>> prepared = members.stream().map(this::audienceMember).toList();

    List<String> requestIds = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (int from = 0; from < prepared.size(); from += MAX_AUDIENCE_MEMBERS_PER_REQUEST) {
      int to = Math.min(prepared.size(), from + MAX_AUDIENCE_MEMBERS_PER_REQUEST);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("destinations", List.of(destination(context, audience.getExternalAudienceId())));
      body.put("audienceMembers", prepared.subList(from, to));
      body.put("encoding", "HEX");
      body.put("consent", consent(request.adUserDataConsent(), request.adPersonalizationConsent()));
      body.put("termsOfService", Map.of("customerMatchTermsOfServiceStatus", "ACCEPTED"));
      body.put("validateOnly", request.validateOnly());

      JsonNode response = apiClient.ingestAudienceMembers(context.accessToken(), body);
      String requestId = response.path("requestId").asText(null);
      if (requestId != null && !requestId.isBlank()) requestIds.add(requestId);
      collectWarnings(response, warnings);
    }

    activityLogService.record(
        companyId,
        audience.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        request.validateOnly()
            ? "GOOGLE_CUSTOMER_MATCH_VALIDATED"
            : "GOOGLE_CUSTOMER_MATCH_UPLOADED",
        "AUDIENCE",
        audience.getExternalAudienceId(),
        null,
        Map.of(
            "submittedCount", members.size(),
            "requestCount", requestIds.size(),
            "validateOnly", request.validateOnly()),
        Map.of(
            "adUserDataConsent", request.adUserDataConsent(),
            "adPersonalizationConsent", request.adPersonalizationConsent()));

    return new GoogleDataManagerUploadResponse(
        List.copyOf(requestIds), members.size(), request.validateOnly(), List.copyOf(warnings));
  }

  public GoogleDataManagerUploadResponse uploadConversionEvents(
      Long companyId,
      Long conversionId,
      GoogleConversionEventUploadRequest request,
      Long actorUserId) {
    AdConversion conversion =
        conversionRepository
            .findByIdAndCompanyId(conversionId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising conversion not found"));

    if (conversion.getProvider() != AdProvider.GOOGLE_ADS || !conversion.isAccessible()) {
      throw new BadRequestException("Google conversion action is not available");
    }

    var context = contextService.requireManaged(companyId, conversion.getAdAccountId());
    List<GoogleConversionEvent> events = request.events();
    if (events == null || events.isEmpty()) {
      throw new BadRequestException("Conversion upload requires at least one event");
    }

    List<Map<String, Object>> prepared = events.stream().map(this::conversionEvent).toList();
    List<String> requestIds = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (int from = 0; from < prepared.size(); from += MAX_EVENTS_PER_REQUEST) {
      int to = Math.min(prepared.size(), from + MAX_EVENTS_PER_REQUEST);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("destinations", List.of(destination(context, conversion.getExternalConversionId())));
      body.put("events", prepared.subList(from, to));
      body.put("consent", consent(request.adUserDataConsent(), request.adPersonalizationConsent()));
      body.put("encoding", "HEX");
      body.put("validateOnly", request.validateOnly());

      JsonNode response = apiClient.ingestEvents(context.accessToken(), body);
      String requestId = response.path("requestId").asText(null);
      if (requestId != null && !requestId.isBlank()) requestIds.add(requestId);
      collectWarnings(response, warnings);
    }

    activityLogService.record(
        companyId,
        conversion.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        request.validateOnly()
            ? "GOOGLE_CONVERSION_EVENTS_VALIDATED"
            : "GOOGLE_CONVERSION_EVENTS_UPLOADED",
        "CONVERSION",
        conversion.getExternalConversionId(),
        null,
        Map.of(
            "submittedCount", events.size(),
            "requestCount", requestIds.size(),
            "validateOnly", request.validateOnly()),
        Map.of(
            "adUserDataConsent", request.adUserDataConsent(),
            "adPersonalizationConsent", request.adPersonalizationConsent()));

    return new GoogleDataManagerUploadResponse(
        List.copyOf(requestIds), events.size(), request.validateOnly(), List.copyOf(warnings));
  }

  private Map<String, Object> audienceMember(GoogleCustomerMatchMember member) {
    if (member == null) {
      throw new BadRequestException("Customer Match member cannot be null");
    }

    boolean hasContact = hasValues(member.emails()) || hasValues(member.phoneNumbers());
    boolean hasMobile = hasValues(member.mobileIds());
    boolean hasUserId = member.userId() != null && !member.userId().isBlank();

    int modes = (hasContact ? 1 : 0) + (hasMobile ? 1 : 0) + (hasUserId ? 1 : 0);
    if (modes != 1) {
      throw new BadRequestException(
          "Each Customer Match member must use exactly one identifier mode: contact info, mobile IDs, or user ID");
    }

    if (hasContact) {
      List<Map<String, String>> identifiers = new ArrayList<>();
      if (member.emails() != null) {
        member.emails().stream()
            .filter(this::hasText)
            .limit(10)
            .forEach(
                email -> identifiers.add(Map.of("emailAddress", sha256(normalizeEmail(email)))));
      }
      if (member.phoneNumbers() != null) {
        member.phoneNumbers().stream()
            .filter(this::hasText)
            .limit(Math.max(0, 10 - identifiers.size()))
            .forEach(
                phone -> identifiers.add(Map.of("phoneNumber", sha256(normalizePhone(phone)))));
      }
      if (identifiers.isEmpty()) {
        throw new BadRequestException(
            "Customer Match contact member requires an email or phone number");
      }
      return Map.of("compositeData", Map.of("userData", Map.of("userIdentifiers", identifiers)));
    }

    if (hasMobile) {
      List<String> mobileIds =
          member.mobileIds().stream()
              .filter(this::hasText)
              .map(String::trim)
              .distinct()
              .limit(10)
              .toList();
      return Map.of("mobileData", Map.of("mobileIds", mobileIds));
    }

    return Map.of("userIdData", Map.of("userId", member.userId().trim()));
  }

  private Map<String, Object> conversionEvent(GoogleConversionEvent event) {
    if (event == null || event.eventTimestamp() == null) {
      throw new BadRequestException("Conversion event timestamp is required");
    }

    String eventSource =
        event.eventSource() == null || event.eventSource().isBlank()
            ? "OTHER"
            : event.eventSource().trim().toUpperCase();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("eventTimestamp", event.eventTimestamp().toString());
    result.put("eventSource", eventSource);

    if (hasText(event.transactionId())) {
      result.put("transactionId", event.transactionId().trim());
    }
    if (event.conversionValue() != null) {
      result.put("conversionValue", event.conversionValue());
    }
    if (hasText(event.currency())) {
      result.put("currency", event.currency().trim().toUpperCase(Locale.ROOT));
    }
    if (hasText(event.userId())) {
      result.put("userId", event.userId().trim());
    }

    Map<String, Object> adIdentifiers = new LinkedHashMap<>();
    putIfText(adIdentifiers, "gclid", event.gclid());
    putIfText(adIdentifiers, "gbraid", event.gbraid());
    putIfText(adIdentifiers, "wbraid", event.wbraid());
    if (!adIdentifiers.isEmpty()) result.put("adIdentifiers", adIdentifiers);

    List<Map<String, String>> identifiers = new ArrayList<>();
    if (event.emails() != null) {
      event.emails().stream()
          .filter(this::hasText)
          .limit(10)
          .forEach(email -> identifiers.add(Map.of("emailAddress", sha256(normalizeEmail(email)))));
    }
    if (event.phoneNumbers() != null) {
      event.phoneNumbers().stream()
          .filter(this::hasText)
          .limit(Math.max(0, 10 - identifiers.size()))
          .forEach(phone -> identifiers.add(Map.of("phoneNumber", sha256(normalizePhone(phone)))));
    }
    if (!identifiers.isEmpty()) {
      result.put("userData", Map.of("userIdentifiers", identifiers));
    }

    if (adIdentifiers.isEmpty()
        && identifiers.isEmpty()
        && !hasText(event.userId())
        && !hasText(event.transactionId())) {
      throw new BadRequestException(
          "Conversion event needs a click ID, user data, user ID, or transaction ID for matching");
    }

    return result;
  }

  private Map<String, Object> destination(
      GoogleAdsAccountContextService.Context context, String productDestinationId) {
    Map<String, Object> destination = new LinkedHashMap<>();
    destination.put(
        "operatingAccount",
        Map.of(
            "accountId",
            context.account().getExternalAccountId().replace("-", ""),
            "accountType",
            "GOOGLE_ADS"));

    if (context.loginCustomerId() != null && !context.loginCustomerId().isBlank()) {
      destination.put(
          "loginAccount",
          Map.of(
              "accountId",
              context.loginCustomerId().replace("-", ""),
              "accountType",
              "GOOGLE_ADS"));
    }
    destination.put("productDestinationId", productDestinationId);
    return destination;
  }

  private Map<String, Object> consent(boolean adUserData, boolean adPersonalization) {
    return Map.of(
        "adUserData", adUserData ? "CONSENT_GRANTED" : "CONSENT_DENIED",
        "adPersonalization", adPersonalization ? "CONSENT_GRANTED" : "CONSENT_DENIED");
  }

  private void collectWarnings(JsonNode response, List<String> warnings) {
    JsonNode values = response.path("fieldWarnings");
    if (!values.isArray()) return;
    for (JsonNode warning : values) {
      String message = warning.path("message").asText(null);
      if (message == null || message.isBlank()) message = warning.toString();
      warnings.add(message);
    }
  }

  private String normalizeEmail(String raw) {
    String value = raw.trim().toLowerCase(Locale.ROOT);
    int at = value.lastIndexOf('@');
    if (at <= 0 || at == value.length() - 1) {
      throw new BadRequestException("Customer email is invalid");
    }

    String local = value.substring(0, at);
    String domain = value.substring(at + 1);
    if ("gmail.com".equals(domain) || "googlemail.com".equals(domain)) {
      local = local.replace(".", "");
      domain = "gmail.com";
    }
    return local + "@" + domain;
  }

  private String normalizePhone(String raw) {
    String value = raw.trim().replaceAll("[\\s().-]", "");
    if (!value.startsWith("+") || value.length() < 8 || !value.substring(1).matches("\\d+")) {
      throw new BadRequestException(
          "Customer phone numbers must be supplied in E.164 format, for example +919876543210");
    }
    return value;
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash Google first-party data", exception);
    }
  }

  private boolean hasValues(List<String> values) {
    return values != null && values.stream().anyMatch(this::hasText);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void putIfText(Map<String, Object> target, String key, String value) {
    if (hasText(value)) target.put(key, value.trim());
  }
}
