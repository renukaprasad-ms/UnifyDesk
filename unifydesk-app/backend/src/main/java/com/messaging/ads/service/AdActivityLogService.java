package com.messaging.ads.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdActivityLog;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdActivityLogService {

  private final AdActivityLogRepository repository;
  private final ObjectMapper objectMapper;

  public void record(
      Long companyId,
      Long adAccountId,
      AdProvider provider,
      Long actorUserId,
      String action,
      String entityType,
      String entityId,
      Object before,
      Object after,
      Object metadata) {
    AdActivityLog log = new AdActivityLog();
    log.setCompanyId(companyId);
    log.setAdAccountId(adAccountId);
    log.setProvider(provider);
    log.setActorUserId(actorUserId);
    log.setAction(action);
    log.setEntityType(entityType);
    log.setEntityId(entityId);
    log.setBeforeJson(json(before));
    log.setAfterJson(json(after));
    log.setMetadataJson(json(metadata));
    repository.save(log);
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Ad Center activity", exception);
    }
  }
}
