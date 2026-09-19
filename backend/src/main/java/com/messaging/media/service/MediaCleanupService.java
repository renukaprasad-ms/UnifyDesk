package com.messaging.media.service;

import com.messaging.media.config.MediaValidationProperties;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.provider.StorageProviderResolver;
import com.messaging.media.repository.MediaRepository;
import com.messaging.storage.adapter.MediaStorageAccountingAdapter;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaCleanupService {

  private final MediaRepository mediaRepository;
  private final MediaValidationProperties validationProperties;
  private final StorageProviderResolver storageProviderResolver;
  private final MediaStorageAccountingAdapter storageAccounting;

  public int cleanupExpiredPendingUploads() {
    Instant cutoff = Instant.now().minus(validationProperties.getPendingTtl());
    int deleted;
    int totalDeleted = 0;
    do {
      deleted = cleanupExpiredPendingUploads(cutoff, 100);
      totalDeleted += deleted;
    } while (deleted == 100);
    return totalDeleted;
  }

  @org.springframework.transaction.annotation.Transactional
  public int cleanupExpiredPendingUploads(Instant cutoff, int batchSize) {
    List<Media> expired =
        mediaRepository.findByStatusAndCreatedAtBefore(
            MediaStatus.PENDING, cutoff, PageRequest.of(0, batchSize));
    int cleaned = 0;
    for (Media media : expired) {
      try {
        storageProviderResolver
            .resolve(media.getStorageProvider())
            .delete(media.getBucket(), media.getObjectKey());
      } catch (RuntimeException exception) {
        // Storage cleanup failure does not block marking the expired upload deleted.
      }
      storageAccounting.release(media);
      media.setStatus(MediaStatus.DELETED);
      media.setDeletedAt(Instant.now());
      mediaRepository.saveAndFlush(media);
      cleaned++;
    }
    return cleaned;
  }
}
