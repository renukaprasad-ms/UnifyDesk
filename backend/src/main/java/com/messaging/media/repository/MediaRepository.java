package com.messaging.media.repository;

import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {

  List<Media> findByStatusAndCreatedAtBefore(MediaStatus status, Instant createdBefore);

  List<Media> findByStatusAndCreatedAtBefore(
      MediaStatus status, Instant createdBefore, Pageable pageable);
}
