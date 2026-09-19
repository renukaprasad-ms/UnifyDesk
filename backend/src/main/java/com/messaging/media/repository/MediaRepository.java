package com.messaging.media.repository;

import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {

  @Query(
      "select coalesce(sum(m.sizeBytes), 0) from Media m "
          + "where m.companyId = :companyId and m.status in :statuses")
  long sumSizeBytesByCompanyAndStatuses(
      @Param("companyId") Long companyId, @Param("statuses") List<MediaStatus> statuses);

  List<Media> findByStatusAndCreatedAtBefore(MediaStatus status, Instant createdBefore);

  List<Media> findByStatusAndCreatedAtBefore(
      MediaStatus status, Instant createdBefore, Pageable pageable);
}
