package com.messaging.ads.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaCustomerListMembersRequest;
import com.messaging.ads.meta.dto.MetaCustomerListMembersResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsCustomerListService {

  private static final int META_BATCH_SIZE = 10_000;
  private static final Set<String> SUPPORTED_SCHEMA =
      Set.of(
          "EMAIL",
          "PHONE",
          "FN",
          "LN",
          "FI",
          "CT",
          "ST",
          "ZIP",
          "COUNTRY",
          "GEN",
          "DOBY",
          "DOBM",
          "DOBD",
          "EXTERN_ID");

  private final AdAccountRepository accountRepository;
  private final AdAudienceRepository audienceRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public MetaCustomerListMembersResponse mutateMembers(
      Long companyId,
      Long audienceId,
      MetaCustomerListMembersRequest request,
      Long actorUserId) {
    AdAudience audience =
        audienceRepository
            .findByIdAndCompanyId(audienceId, companyId)
            .orElseThrow(() -> new NotFoundException("Meta audience not found"));
    if (audience.getProvider() != AdProvider.META_ADS
        || !audience.isAccessible()
        || !"CUSTOM".equalsIgnoreCase(audience.getSubtype())) {
      throw new BadRequestException(
          "Meta customer-list member uploads require an accessible CUSTOM audience");
    }

    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(audience.getAdAccountId(), companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }

    List<String> schema = normalizeSchema(request.schema());
    List<List<String>> rows = normalizeRows(schema, request.rows(), Boolean.TRUE.equals(request.preHashed()));
    if (rows.isEmpty()) {
      throw new BadRequestException("At least one valid customer-list row is required");
    }

    String token = token(companyId, account);
    int providerReceivedRows = 0;
    int providerInvalidRows = 0;
    int batchCount = 0;

    for (int start = 0; start < rows.size(); start += META_BATCH_SIZE) {
      int end = Math.min(rows.size(), start + META_BATCH_SIZE);
      List<List<String>> batch = rows.subList(start, end);
      JsonNode response =
          apiClient.customAudienceUsers(
              token,
              audience.getExternalAudienceId(),
              request.operation() == MetaCustomerListMembersRequest.Operation.REMOVE,
              Map.of(
                  "payload",
                  Map.of(
                      "schema", schema,
                      "data", batch)));
      providerReceivedRows += integer(response, "num_received", batch.size());
      providerInvalidRows += integer(response, "num_invalid_entries", 0);
      batchCount++;
    }

    MetaCustomerListMembersResponse result =
        new MetaCustomerListMembersResponse(
            audience.getExternalAudienceId(),
            request.operation(),
            rows.size(),
            batchCount,
            providerReceivedRows,
            providerInvalidRows);

    activityLogService.record(
        companyId,
        audience.getAdAccountId(),
        AdProvider.META_ADS,
        actorUserId,
        request.operation() == MetaCustomerListMembersRequest.Operation.ADD
            ? "META_CUSTOM_AUDIENCE_MEMBERS_ADDED"
            : "META_CUSTOM_AUDIENCE_MEMBERS_REMOVED",
        "AUDIENCE",
        audience.getExternalAudienceId(),
        null,
        Map.of(
            "submittedRows", rows.size(),
            "batchCount", batchCount,
            "providerReceivedRows", providerReceivedRows,
            "providerInvalidRows", providerInvalidRows,
            "schema", schema),
        Map.of("localAudienceId", audience.getId()));

    return result;
  }

  private List<String> normalizeSchema(List<String> values) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        throw new BadRequestException("Meta customer-list schema values cannot be blank");
      }
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if (!SUPPORTED_SCHEMA.contains(normalized)) {
        throw new BadRequestException("Unsupported Meta customer-list schema: " + normalized);
      }
      if (!result.add(normalized)) {
        throw new BadRequestException("Meta customer-list schema values must be unique");
      }
    }
    if (result.isEmpty()) {
      throw new BadRequestException("Meta customer-list schema is required");
    }
    return List.copyOf(result);
  }

  private List<List<String>> normalizeRows(
      List<String> schema, List<List<String>> rows, boolean preHashed) {
    List<List<String>> result = new ArrayList<>();
    for (List<String> row : rows) {
      if (row == null || row.size() != schema.size()) {
        throw new BadRequestException(
            "Each Meta customer-list row must match the schema column count");
      }
      List<String> normalized = new ArrayList<>(schema.size());
      boolean anyValue = false;
      for (int index = 0; index < schema.size(); index++) {
        String key = schema.get(index);
        String raw = row.get(index) == null ? "" : row.get(index).trim();
        if (raw.isBlank()) {
          normalized.add("");
          continue;
        }
        if ("EXTERN_ID".equals(key)) {
          normalized.add(raw);
          anyValue = true;
          continue;
        }
        if (preHashed) {
          if (!raw.matches("(?i)[0-9a-f]{64}")) {
            throw new BadRequestException(
                "Pre-hashed Meta customer-list identifiers must be lowercase/uppercase SHA-256 hex");
          }
          normalized.add(raw.toLowerCase(Locale.ROOT));
          anyValue = true;
          continue;
        }
        String normalizedKey = normalizeKey(key, raw);
        if (normalizedKey.isBlank()) {
          normalized.add("");
          continue;
        }
        normalized.add(sha256(normalizedKey));
        anyValue = true;
      }
      if (anyValue) result.add(List.copyOf(normalized));
    }
    return List.copyOf(result);
  }

  private String normalizeKey(String key, String raw) {
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "EMAIL" -> value.replaceAll("^\\.+|\\.+$", "");
      case "PHONE", "DOBY" -> value.replaceAll("[^0-9]", "");
      case "DOBM", "DOBD" -> {
        String digits = value.replaceAll("[^0-9]", "");
        yield digits.length() == 1 ? "0" + digits : digits;
      }
      case "FN", "LN", "FI", "CT", "ST" -> value.replaceAll("[^a-z]", "");
      case "ZIP" -> value.split("-", 2)[0];
      case "COUNTRY" -> {
        String letters = value.replaceAll("[^a-z]", "");
        yield letters.substring(0, Math.min(2, letters.length()));
      }
      case "GEN" -> value.isBlank() ? "" : value.substring(0, 1);
      default -> value;
    };
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private int integer(JsonNode response, String field, int fallback) {
    if (response == null || response.isNull()) return fallback;
    JsonNode value = response.get(field);
    if (value == null || value.isNull()) return fallback;
    if (value.isNumber()) return value.asInt(fallback);
    try {
      return Integer.parseInt(value.asText());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private String token(Long companyId, AdAccount account) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    return tokenService.read(connection.getId()).token().accessToken();
  }
}
