package com.messaging.ads.dto;

import com.messaging.ads.entity.AdActivityLog;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdActivityLogResponse(
    String id,
    String companyId,
    String adAccountId,
    AdProvider provider,
    String actorUserId,
    String action,
    String entityType,
    String entityId,
    String beforeJson,
    String afterJson,
    String metadataJson,
    Instant createdAt) {

  public static AdActivityLogResponse from(AdActivityLog log) {
    return new AdActivityLogResponse(
        log.getId().toString(),
        log.getCompanyId().toString(),
        log.getAdAccountId() == null ? null : log.getAdAccountId().toString(),
        log.getProvider(),
        log.getActorUserId() == null ? null : log.getActorUserId().toString(),
        log.getAction(),
        log.getEntityType(),
        log.getEntityId(),
        log.getBeforeJson(),
        log.getAfterJson(),
        log.getMetadataJson(),
        log.getCreatedAt());
  }
}
