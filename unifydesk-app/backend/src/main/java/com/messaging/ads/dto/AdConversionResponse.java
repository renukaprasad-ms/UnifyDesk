package com.messaging.ads.dto;

import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.time.Instant;

public record AdConversionResponse(
    String id, String companyId, String adAccountId, AdProvider provider,
    String externalConversionId, String name, String description, String customEventType,
    String eventSourceType, String pixelId, String ruleJson, String dataSourcesJson,
    BigDecimal defaultConversionValue, boolean archived, boolean unavailable,
    Instant firstFiredAt, Instant lastFiredAt, Instant providerCreatedAt,
    boolean accessible, Instant lastSyncAt) {
  public static AdConversionResponse from(AdConversion value) {
    return new AdConversionResponse(
        value.getId().toString(), value.getCompanyId().toString(), value.getAdAccountId().toString(),
        value.getProvider(), value.getExternalConversionId(), value.getName(), value.getDescription(),
        value.getCustomEventType(), value.getEventSourceType(), value.getPixelId(), value.getRuleJson(),
        value.getDataSourcesJson(), value.getDefaultConversionValue(), value.isArchived(),
        value.isUnavailable(), value.getFirstFiredAt(), value.getLastFiredAt(),
        value.getProviderCreatedAt(), value.isAccessible(), value.getLastSyncAt());
  }
}
