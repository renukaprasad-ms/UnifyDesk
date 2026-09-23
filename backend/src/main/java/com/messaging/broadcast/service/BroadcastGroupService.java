package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.BroadcastGroup;
import com.messaging.broadcast.enums.BroadcastGroupMemberStatus;
import com.messaging.broadcast.repository.BroadcastGroupMemberRepository;
import com.messaging.broadcast.repository.BroadcastGroupRepository;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastGroupService {

  private final BroadcastGroupRepository groupRepository;
  private final BroadcastGroupMemberRepository memberRepository;

  @Transactional
  public BroadcastGroupResponse create(
      Long companyId, Long userId, CreateBroadcastGroupRequest request) {
    BroadcastGroup group = new BroadcastGroup();
    group.setCompanyId(companyId);
    group.setCreatedByUserId(userId);
    group.setChannelType(request.channelType());
    group.setName(request.name().trim());
    group.setDescription(trimToNull(request.description()));
    return BroadcastGroupResponse.from(groupRepository.save(group));
  }

  @Transactional
  public BroadcastGroupResponse update(
      Long companyId, Long groupId, UpdateBroadcastGroupRequest request) {
    BroadcastGroup group = require(companyId, groupId);
    group.setName(request.name().trim());
    group.setDescription(trimToNull(request.description()));
    return BroadcastGroupResponse.from(groupRepository.save(group));
  }

  @Transactional(readOnly = true)
  public Page<BroadcastGroupResponse> list(
      Long companyId, com.messaging.channel.enums.ChannelType channelType, Pageable pageable) {
    return groupRepository
        .findByCompanyIdAndChannelTypeOrderByUpdatedAtDesc(companyId, channelType, pageable)
        .map(BroadcastGroupResponse::from);
  }

  @Transactional(readOnly = true)
  public BroadcastGroupResponse get(Long companyId, Long groupId) {
    return BroadcastGroupResponse.from(require(companyId, groupId));
  }

  @Transactional(readOnly = true)
  public Page<BroadcastMemberResponse> members(Long companyId, Long groupId, Pageable pageable) {
    BroadcastGroup group = require(companyId, groupId);
    return memberRepository
        .findByGroupIdAndStatusOrderByIdAsc(
            group.getId(), BroadcastGroupMemberStatus.ACTIVE, pageable)
        .map(BroadcastMemberResponse::from);
  }

  public BroadcastGroup require(Long companyId, Long groupId) {
    return groupRepository
        .findByIdAndCompanyId(groupId, companyId)
        .orElseThrow(() -> new NotFoundException("Broadcast group not found"));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
