package com.messaging.lifecycle.service;

import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeletionCleanupService {

  public void cleanup(EntityDeletionRequestedEvent event) {
    // Business/audit rows are intentionally retained after soft delete.
    // Provider/media-specific physical cleanup can be attached here without
    // extending request latency or weakening immediate access revocation.
    log.info(
        "Deletion cleanup processed entityType={} entityId={} companyId={}",
        event.entityType(),
        event.entityId(),
        event.companyId());
  }
}
