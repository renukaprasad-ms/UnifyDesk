package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastGroupMember;
import com.messaging.broadcast.enums.BroadcastGroupMemberStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastGroupMemberRepository extends JpaRepository<BroadcastGroupMember, Long> {
  Page<BroadcastGroupMember> findByGroupIdAndStatusOrderByIdAsc(
      Long groupId, BroadcastGroupMemberStatus status, Pageable pageable);

  Optional<BroadcastGroupMember> findByIdAndGroupId(Long id, Long groupId);

  @Query(
      """
      select max(member.id)
      from BroadcastGroupMember member
      where member.groupId = :groupId
        and member.status = com.messaging.broadcast.enums.BroadcastGroupMemberStatus.ACTIVE
      """)
  Long findMaxActiveMemberId(@Param("groupId") Long groupId);

  @Query(
      """
      select member
      from BroadcastGroupMember member
      where member.groupId = :groupId
        and member.id > :afterId
        and member.id <= :maxId
        and member.activeSince <= :snapshotAt
        and (member.disabledAt is null or member.disabledAt > :snapshotAt)
      order by member.id asc
      """)
  List<BroadcastGroupMember> findSnapshotPage(
      @Param("groupId") Long groupId,
      @Param("afterId") Long afterId,
      @Param("maxId") Long maxId,
      @Param("snapshotAt") Instant snapshotAt,
      Pageable pageable);
}
