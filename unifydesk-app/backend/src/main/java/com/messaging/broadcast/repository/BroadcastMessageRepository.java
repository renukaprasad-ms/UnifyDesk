package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, Long> {
  Page<BroadcastMessage> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);

  Page<BroadcastMessage> findByCompanyIdAndChannelTypeOrderByCreatedAtDesc(
      Long companyId, ChannelType channelType, Pageable pageable);

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

  @Modifying
  @Query(
      value =
          """
          update broadcast_messages message
          set status = 'SCHEDULED',
              updated_at = :now
          where message.status = 'QUEUED'
            and message.scheduled_at is not null
            and message.updated_at < :staleBefore
            and not exists (
              select 1
              from broadcast_runs run
              where run.broadcast_message_id = message.id
            )
          """,
      nativeQuery = true)
  int recoverStaleScheduledClaims(
      @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

  long countByGroupIdAndStatusIn(Long groupId, List<BroadcastMessageStatus> statuses);
}
