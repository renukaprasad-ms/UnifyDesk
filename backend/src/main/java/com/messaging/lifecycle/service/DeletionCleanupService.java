package com.messaging.lifecycle.service;

import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.provider.StorageProviderResolver;
import com.messaging.media.repository.MediaRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletionCleanupService {

  private final MediaRepository mediaRepository;
  private final StorageProviderResolver storageProviderResolver;

  public void cleanup(EntityDeletionRequestedEvent event) {
    switch (event.entityType()) {
      case USER -> cleanupPersonalMedia(event.entityId());
      case COMPANY -> cleanupCompanyMedia(event.entityId());
      case CHANNEL_CONNECTION, COMPANY_ROLE -> {
        // Relational state is already disabled/revoked in the transaction that queued this event.
      }
    }

    log.info(
        "Deletion cleanup processed entityType={} entityId={} companyId={}",
        event.entityType(),
        event.entityId(),
        event.companyId());
  }

  private void cleanupPersonalMedia(Long userId) {
    cleanupMedia(
        mediaRepository.findByOwnerUserIdAndCompanyIdIsNullAndStatusNot(
            userId, MediaStatus.DELETED));
  }

  private void cleanupCompanyMedia(Long companyId) {
    cleanupMedia(mediaRepository.findByCompanyIdAndStatusNot(companyId, MediaStatus.DELETED));
  }

  private void cleanupMedia(List<Media> media) {
    Instant now = Instant.now();
    for (Media item : media) {
      item.setStatus(MediaStatus.DELETED);
      item.setDeletedAt(now);
      mediaRepository.save(item);

      try {
        storageProviderResolver
            .resolve(item.getStorageProvider())
            .delete(item.getBucket(), item.getObjectKey());
      } catch (RuntimeException exception) {
        log.warn(
            "Physical media cleanup failed mediaId={} provider={} reason={}",
            item.getId(),
            item.getStorageProvider(),
            exception.getClass().getSimpleName());
      }
    }
  }
}
