package com.messaging.lifecycle.service;

import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import com.messaging.lifecycle.enums.LifecycleEntityType;
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
    if (event.entityType() == LifecycleEntityType.USER) {
      cleanupPersonalMedia(event.entityId());
    }

    log.info(
        "Deletion cleanup processed entityType={} entityId={} companyId={}",
        event.entityType(),
        event.entityId(),
        event.companyId());
  }

  private void cleanupPersonalMedia(Long userId) {
    List<Media> media =
        mediaRepository.findByOwnerUserIdAndCompanyIdIsNullAndStatusNot(
            userId, MediaStatus.DELETED);

    for (Media item : media) {
      item.setStatus(MediaStatus.DELETED);
      item.setDeletedAt(Instant.now());
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
