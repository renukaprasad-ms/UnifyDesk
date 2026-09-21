package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastGroupMember;
import com.messaging.broadcast.enums.BroadcastGroupMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastGroupMemberRepository extends JpaRepository<BroadcastGroupMember, Long> {
  Page<BroadcastGroupMember> findByGroupIdAndStatusOrderByIdAsc(
      Long groupId, BroadcastGroupMemberStatus status, Pageable pageable);

  List<BroadcastGroupMember> findByGroupIdAndStatusAndIdGreaterThanOrderByIdAsc(
      Long groupId,
      BroadcastGroupMemberStatus status,
      Long afterId,
      Pageable pageable);

  Optional<BroadcastGroupMember> findByIdAndGroupId(Long id, Long groupId);
}
