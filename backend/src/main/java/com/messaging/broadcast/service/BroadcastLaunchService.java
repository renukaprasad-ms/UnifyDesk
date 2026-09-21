package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastLaunchService {

  private final BroadcastMessageRepository messageRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastStatsRepository statsRepository;
  private final BroadcastGroupRepository groupRepository;
  private final BroadcastGroupMemberRepository memberRepository;
  private final BroadcastSenderService senderService;
  private final BroadcastStateMachine stateMachine;
  private final BroadcastOutboxService outboxService;

  @Transactional
  public BroadcastRun launch(Long companyId, Long broadcastId) {
    return launchInternal(companyId, broadcastId, false);
  }

  @Transactional
  public BroadcastRun launchClaimed(Long companyId, Long broadcastId) {
    return launchInternal(companyId, broadcastId, true);
  }

  private BroadcastRun launchInternal(
      Long companyId, Long broadcastId, boolean allowClaimed) {
    BroadcastMessage message =
        messageRepository
            .findByIdAndCompanyId(broadcastId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));

    BroadcastRun existing = runRepository.findByBroadcastMessageId(broadcastId).orElse(null);
    if (existing != null) return existing;

    if (!(allowClaimed && message.getStatus() == BroadcastMessageStatus.QUEUED)) {
      stateMachine.requireSendable(message.getStatus());
    }

    BroadcastGroup group =
        groupRepository
            .findByIdAndCompanyId(message.getGroupId(), companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast group not found"));
    if (group.getChannelType() != ChannelType.EMAIL
        || message.getChannelType() != ChannelType.EMAIL) {
      throw new BadRequestException("Broadcast execution is currently available for email");
    }
    if (message.getSenderId() == null) {
      throw new BadRequestException(
          "Choose an Amazon SES sender before launching this broadcast");
    }

    BroadcastSender sender = senderService.requireActive(companyId, message.getSenderId());
    BroadcastSenderConnection senderConnection =
        senderService.requireConnection(companyId, sender.getConnectionId());
    if (sender.getProvider() != BroadcastSenderProvider.AMAZON_SES
        || senderConnection.getProvider() != BroadcastSenderProvider.AMAZON_SES) {
      throw new BadRequestException("New email broadcasts currently require Amazon SES");
    }

    Long maxMemberId = memberRepository.findMaxActiveMemberId(group.getId());
    if (maxMemberId == null) {
      throw new BadRequestException("Broadcast group has no active recipients");
    }

    BroadcastRun run = new BroadcastRun();
    run.setBroadcastMessageId(message.getId());
    run.setCompanyId(companyId);
    run.setGroupId(group.getId());
    run.setSenderId(sender.getId());
    run.setProviderType(sender.getProvider());
    run.setStatus(BroadcastRunStatus.PREPARING);
    run.setSubjectSnapshot(message.getSubject());
    run.setBodyTextSnapshot(message.getBodyText());
    run.setSnapshotMaxMemberId(maxMemberId);
    run = runRepository.saveAndFlush(run);

    BroadcastStats stats = new BroadcastStats();
    stats.setRunId(run.getId());
    statsRepository.save(stats);

    message.setStatus(BroadcastMessageStatus.QUEUED);
    message.setScheduledAt(null);
    messageRepository.save(message);

    outboxService.enqueueRunPrepare(companyId, run.getId());
    return run;
  }
}
