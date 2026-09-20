package com.messaging.channel.microsoft.repository;

import com.messaging.channel.microsoft.entity.MicrosoftSyncState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MicrosoftSyncStateRepository extends JpaRepository<MicrosoftSyncState, Long> {

  Optional<MicrosoftSyncState> findByConnectionIdAndFolderKey(Long connectionId, String folderKey);

  List<MicrosoftSyncState> findByConnectionId(Long connectionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from MicrosoftSyncState s where s.id = :id")
  Optional<MicrosoftSyncState> findByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          """
          select s.*
          from microsoft_sync_states s
          join (
            select distinct on (candidate.connection_id) candidate.id
            from microsoft_sync_states candidate
            where candidate.status in ('PENDING', 'RETRY')
              and (candidate.next_attempt_at is null or candidate.next_attempt_at <= now())
              and not exists (
                select 1
                from microsoft_sync_states running
                where running.connection_id = candidate.connection_id
                  and running.status = 'RUNNING'
              )
            order by candidate.connection_id, candidate.updated_at asc, candidate.folder_key asc
          ) claimable on claimable.id = s.id
          order by s.updated_at asc
          for update of s skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<MicrosoftSyncState> findClaimableForUpdate(@Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          update microsoft_sync_states s
          set status = 'PENDING',
              next_url = s.delta_url,
              next_attempt_at = now(),
              updated_at = now()
          where s.status = 'SYNCED'
            and s.delta_url is not null
            and (s.last_synced_at is null or s.last_synced_at < :cutoff)
            and exists (
              select 1
              from channel_connections c
              where c.id = s.connection_id
                and c.provider_type = 'MICROSOFT'
                and c.status = 'ACTIVE'
            )
          """,
      nativeQuery = true)
  int scheduleSafetySync(@Param("cutoff") Instant cutoff);

  @Modifying
  @Query(
      value =
          """
          update microsoft_sync_states
          set status = 'RETRY',
              claimed_at = null,
              next_attempt_at = now(),
              last_error = 'Recovered stale Microsoft sync worker',
              updated_at = now()
          where status = 'RUNNING'
            and claimed_at is not null
            and claimed_at < :cutoff
          """,
      nativeQuery = true)
  int recoverStale(@Param("cutoff") Instant cutoff);
}
