package com.messaging.media.mapper;

import com.messaging.media.dto.MediaResponse;
import com.messaging.media.entity.Media;
import org.springframework.stereotype.Component;

@Component
public class MediaMapper {

  public MediaResponse toResponse(Media media) {
    return new MediaResponse(
        media.getId().toString(),
        media.getOwnerUserId().toString(),
        media.getStorageProvider(),
        media.getBucket(),
        media.getObjectKey(),
        media.getOriginalFileName(),
        media.getContentType(),
        media.getSizeBytes(),
        media.getMediaType(),
        media.getPurpose(),
        media.getStatus(),
        media.getCreatedAt(),
        media.getCompletedAt(),
        media.getDeletedAt());
  }
}
