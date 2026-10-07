package com.messaging.inbox.repository;

import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.ChannelSyncState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelSyncStateRepository extends JpaRepository<ChannelSyncState, Long> {

  Optional<ChannelSyncState> findByConnectionIdAndProviderType(
      Long connectionId, ProviderType providerType);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from ChannelSyncState s
      where s.connection.id = :connectionId
        and s.providerType = :providerType
      """)
  Optional<ChannelSyncState> findForUpdate(
      @Param("connectionId") Long connectionId,
      @Param("providerType") ProviderType providerType);

  @Query(
      value =
          """
          select s.*
          from channel_sync_states s
          join channel_connections c on c.id = s.connection_id
          where s.provider_type = 'GMAIL'
            and s.pending_history_id is not null
            and c.status = 'ACTIVE'
            and (s.realtime_next_attempt_at is null or s.realtime_next_attempt_at <= now())
            and (
              s.realtime_claimed_at is null
              or s.realtime_claimed_at < :staleCutoff
            )
          order by s.updated_at asc
          for update of s skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<ChannelSyncState> findRealtimeClaimableForUpdate(
      @Param("staleCutoff") Instant staleCutoff, @Param("limit") int limit);

  @Modifying
  @Query(
      value =
          """
          update channel_sync_states
          set realtime_claimed_at = null,
              realtime_next_attempt_at = now(),
              realtime_last_error = 'Recovered stale Gmail realtime worker',
              updated_at = now()
          where provider_type = 'GMAIL'
            and realtime_claimed_at is not null
            and realtime_claimed_at < :cutoff
          """,
      nativeQuery = true)
  int recoverStaleRealtimeClaims(@Param("cutoff") Instant cutoff);

  @Query(
      value =
          """
          select s.*
          from channel_sync_states s
          join channel_connections c on c.id = s.connection_id
          join companies company on company.id = c.company_id
          where s.provider_type = 'IMAP_SMTP'
            and c.status = 'ACTIVE'
            and company.status = 'ACTIVE'
            and (s.incremental_next_attempt_at is null or s.incremental_next_attempt_at <= now())
            and (
              s.incremental_claimed_at is null
              or s.incremental_claimed_at < :staleCutoff
            )
          order by coalesce(s.incremental_next_attempt_at, s.updated_at) asc
          limit :limit
          for update of s skip locked
          """,
      nativeQuery = true)
  List<ChannelSyncState> findImapIncrementalClaimableForUpdate(
      @Param("staleCutoff") Instant staleCutoff, @Param("limit") int limit);

  @Query(
      value =
          """
          select s.*
          from channel_sync_states s
          join channel_connections c on c.id = s.connection_id
          join companies company on company.id = c.company_id
          where s.provider_type = 'ZOHO_MAIL'
            and c.status = 'ACTIVE'
            and company.status = 'ACTIVE'
            and (s.incremental_next_attempt_at is null or s.incremental_next_attempt_at <= now())
            and (
              s.incremental_claimed_at is null
              or s.incremental_claimed_at < :staleCutoff
            )
          order by coalesce(s.incremental_next_attempt_at, s.updated_at) asc
          limit :limit
          for update of s skip locked
          """,
      nativeQuery = true)
  List<ChannelSyncState> findZohoIncrementalClaimableForUpdate(
      @Param("staleCutoff") Instant staleCutoff, @Param("limit") int limit);
}
