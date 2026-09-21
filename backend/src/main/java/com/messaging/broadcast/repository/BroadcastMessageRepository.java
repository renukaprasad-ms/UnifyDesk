package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, Long> {
  Page<BroadcastMessage> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);

  Optional<BroadcastMessage> findByIdAndCompanyId(Long id, Long companyId);

  @Query(
      value =
          """
          select *
          from broadcast_messages
          where status = 'SCHEDULED'
            and scheduled_at <= :now
          order by scheduled_at asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BroadcastMessage> findDueForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  long countByGroupIdAndStatusIn(Long groupId, List<BroadcastMessageStatus> statuses);
}
