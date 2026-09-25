package com.messaging.ads.dto;

import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdProviderConnectionResponse(
    String id,
    String companyId,
    AdProvider provider,
    String displayName,
    AdConnectionStatus status,
    Instant lastSyncAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {

  public static AdProviderConnectionResponse from(AdProviderConnection connection) {
    return new AdProviderConnectionResponse(
        connection.getId().toString(),
        connection.getCompanyId().toString(),
        connection.getProvider(),
        connection.getDisplayName(),
        connection.getStatus(),
        connection.getLastSyncAt(),
        connection.getLastError(),
        connection.getCreatedAt(),
        connection.getUpdatedAt());
  }
}
