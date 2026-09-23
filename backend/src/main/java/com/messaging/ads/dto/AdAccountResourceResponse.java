package com.messaging.ads.dto;

import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdAccountResourceResponse(
    String id,
    String companyId,
    String adAccountId,
    AdProvider provider,
    AdAccountResourceType resourceType,
    String externalResourceId,
    String name,
    boolean accessible,
    Instant lastSyncAt) {

  public static AdAccountResourceResponse from(AdAccountResource resource) {
    return new AdAccountResourceResponse(
        resource.getId().toString(),
        resource.getCompanyId().toString(),
        resource.getAdAccountId().toString(),
        resource.getProvider(),
        resource.getResourceType(),
        resource.getExternalResourceId(),
        resource.getName(),
        resource.isAccessible(),
        resource.getLastSyncAt());
  }
}
