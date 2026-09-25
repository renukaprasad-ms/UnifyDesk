package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleConversionSyncResponse;
import com.messaging.ads.repository.AdConversionRepository;
import java.math.BigDecimal;
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
public class GoogleAdsConversionPersistenceService {

  private final AdConversionRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleConversionSyncResponse persist(
      Long companyId, Long accountId, List<JsonNode> rows) {
    Map<String, AdConversion> existing = new HashMap<>();
    repository
        .findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(companyId, accountId)
        .stream()
        .filter(value -> value.getProvider() == AdProvider.GOOGLE_ADS)
        .forEach(value -> existing.put(value.getExternalConversionId(), value));

    List<AdConversion> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;

    for (JsonNode row : rows) {
      JsonNode snapshot = row.path("conversionAction");
      String externalId = text(snapshot, "id");
      if (externalId == null) continue;
      AdConversion conversion = existing.remove(externalId);
      changed.add(apply(companyId, accountId, conversion, row, syncedAt));
      count++;
    }

    existing.values().forEach(
        conversion -> {
          conversion.setAccessible(false);
          conversion.setLastSyncAt(syncedAt);
          changed.add(conversion);
        });
    repository.saveAll(changed);
    return new GoogleConversionSyncResponse(count);
  }

  @Transactional
  public AdConversion persistOne(Long companyId, Long accountId, JsonNode row) {
    JsonNode snapshot = row.path("conversionAction");
    String externalId = text(snapshot, "id");
    if (externalId == null) {
      throw new com.messaging.common.exception.BadRequestException(
          "Google conversion snapshot is missing an ID");
    }
    AdConversion existing =
        repository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalConversionId(
                companyId, accountId, AdProvider.GOOGLE_ADS, externalId)
            .orElse(null);
    return repository.save(apply(companyId, accountId, existing, row, Instant.now()));
  }

  private AdConversion apply(
      Long companyId,
      Long accountId,
      AdConversion conversion,
      JsonNode row,
      Instant syncedAt) {
    JsonNode snapshot = row.path("conversionAction");
    String externalId = text(snapshot, "id");

    if (conversion == null) {
      conversion = new AdConversion();
      conversion.setCompanyId(companyId);
      conversion.setAdAccountId(accountId);
      conversion.setProvider(AdProvider.GOOGLE_ADS);
      conversion.setExternalConversionId(externalId);
    }

    conversion.setName(first(text(snapshot, "name"), "Google Conversion " + externalId));
    conversion.setDescription(null);
    conversion.setCustomEventType(text(snapshot, "category"));
    conversion.setEventSourceType(first(text(snapshot, "origin"), text(snapshot, "type")));
    conversion.setPixelId(null);
    conversion.setRuleJson(null);
    conversion.setDataSourcesJson(
        json(snapshot.path("attributionModelSettings")));
    conversion.setDefaultConversionValue(
        decimal(snapshot.path("valueSettings").path("defaultValue")));
    String status = text(snapshot, "status");
    conversion.setArchived("REMOVED".equalsIgnoreCase(status));
    conversion.setUnavailable("HIDDEN".equalsIgnoreCase(status));
    conversion.setAccessible(!"REMOVED".equalsIgnoreCase(status));
    conversion.setProviderPayloadJson(json(row));
    conversion.setLastSyncAt(syncedAt);
    return conversion;
  }

  private BigDecimal decimal(JsonNode value) {
    if (value == null || value.isNull() || value.isMissingNode()) return null;
    String text = value.asText(null);
    if (text == null || text.isBlank()) return null;
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ignored) {
      return null;
    }
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
      throw new IllegalStateException("Unable to serialize Google conversion snapshot", exception);
    }
  }
}
