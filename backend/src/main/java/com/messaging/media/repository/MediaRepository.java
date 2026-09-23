package com.messaging.media.repository;

import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<Media, Long> {

  @Query(
      "select coalesce(sum(m.sizeBytes), 0) from Media m "
          + "where m.companyId = :companyId and m.status in :statuses")
  long sumSizeBytesByCompanyAndStatuses(
      @Param("companyId") Long companyId, @Param("statuses") List<MediaStatus> statuses);

  List<Media> findByStatusAndCreatedAtBefore(MediaStatus status, Instant createdBefore);

  List<Media> findByOwnerUserIdAndCompanyIdIsNullAndStatusNot(Long ownerUserId, MediaStatus status);

  List<Media> findByOwnerUserIdAndCompanyIdIsNullAndStatusNot(
      Long ownerUserId, MediaStatus status, Pageable pageable);

  List<Media> findByCompanyIdAndStatusNot(Long companyId, MediaStatus status);

  List<Media> findByCompanyIdAndStatusNot(
      Long companyId, MediaStatus status, Pageable pageable);

  List<Media> findByCompanyIdAndStatusOrderByCreatedAtDesc(
      Long companyId, MediaStatus status, Pageable pageable);

  List<Media> findByStatusAndCreatedAtBefore(
      MediaStatus status, Instant createdBefore, Pageable pageable);

  @Query(
      value =
          """
          select purpose as purpose, status as status, coalesce(sum(size_bytes), 0) as bytes
          from media
          where company_id = :companyId
            and status in ('ACTIVE', 'PENDING')
          group by purpose, status
          """,
      nativeQuery = true)
  List<MediaStorageAggregate> aggregateStorageByCompany(@Param("companyId") Long companyId);

  interface MediaStorageAggregate {
    String getPurpose();

    String getStatus();

    Long getBytes();
  }
}
