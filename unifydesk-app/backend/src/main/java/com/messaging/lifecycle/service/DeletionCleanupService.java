package com.messaging.lifecycle.service;

import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.provider.StorageProviderResolver;
import com.messaging.media.repository.MediaRepository;
import com.messaging.storage.adapter.MediaStorageAccountingAdapter;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletionCleanupService {

  private final MediaRepository mediaRepository;
  private final StorageProviderResolver storageProviderResolver;
  private final MediaStorageAccountingAdapter storageAccounting;

  public void cleanup(EntityDeletionRequestedEvent event) {
    switch (event.entityType()) {
      case USER -> cleanupPersonalMediaBatch(event.entityId(), 500);
      case COMPANY -> cleanupCompanyMediaBatch(event.entityId(), 500);
      case CHANNEL_CONNECTION, COMPANY_ROLE -> {
        // Relational state is already disabled/revoked by the owning lifecycle.
      }
    }
  }

  public int cleanupCompanyMediaBatch(Long companyId, int batchSize) {
    List<Media> media =
        mediaRepository.findByCompanyIdAndStatusNot(
            companyId, MediaStatus.DELETED, PageRequest.of(0, Math.max(1, batchSize)));
    return cleanupMedia(media);
  }

  public int cleanupPersonalMediaBatch(Long userId, int batchSize) {
    List<Media> media =
        mediaRepository.findByOwnerUserIdAndCompanyIdIsNullAndStatusNot(
            userId, MediaStatus.DELETED, PageRequest.of(0, Math.max(1, batchSize)));
    return cleanupMedia(media);
  }

  @Transactional
  protected int cleanupMedia(List<Media> media) {
    int cleaned = 0;
    for (Media item : media) {
      if (item.getStatus() != MediaStatus.DELETE_PENDING) {
        storageAccounting.release(item);
        item.setStatus(MediaStatus.DELETE_PENDING);
        mediaRepository.saveAndFlush(item);
      }

      try {
        storageProviderResolver
            .resolve(item.getStorageProvider())
            .delete(item.getBucket(), item.getObjectKey());
      } catch (RuntimeException exception) {
        log.warn(
            "Physical media cleanup deferred mediaId={} provider={} reason={}",
            item.getId(),
            item.getStorageProvider(),
            exception.getClass().getSimpleName());
        throw exception;
      }

      item.setStatus(MediaStatus.DELETED);
      item.setDeletedAt(Instant.now());
      mediaRepository.saveAndFlush(item);
      cleaned++;
    }
    return cleaned;
  }
}
