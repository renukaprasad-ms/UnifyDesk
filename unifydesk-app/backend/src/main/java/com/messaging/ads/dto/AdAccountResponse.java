package com.messaging.ads.dto;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdAccountResponse(
    String id,
    String companyId,
    String connectionId,
    AdProvider provider,
    String externalAccountId,
    String name,
    String externalBusinessId,
    String currency,
    String timezone,
    AdAccountStatus status,
    boolean primaryAccount,
    boolean managed,
    boolean accessible,
    Instant lastSyncAt,
    Instant createdAt,
    Instant updatedAt) {

  public static AdAccountResponse from(AdAccount account) {
    return new AdAccountResponse(
        account.getId().toString(),
        account.getCompanyId().toString(),
        account.getConnectionId().toString(),
        account.getProvider(),
        account.getExternalAccountId(),
        account.getName(),
        account.getExternalBusinessId(),
        account.getCurrency(),
        account.getTimezone(),
        account.getStatus(),
        account.isPrimaryAccount(),
        account.isManaged(),
        account.isAccessible(),
        account.getLastSyncAt(),
        account.getCreatedAt(),
        account.getUpdatedAt());
  }
}
