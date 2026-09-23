package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import com.messaging.emailtemplate.service.EmailTemplateProviderBindingService;
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
  private final EmailTemplateProviderBindingService templateProviderBindingService;

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
    if (group.getChannelType() != message.getChannelType()) {
      throw new BadRequestException("Broadcast group and message channel do not match");
    }
    if (group.getChannelType() == ChannelType.WHATSAPP) {
      throw new BadRequestException("WhatsApp Broadcast execution is not implemented yet");
    }
    if (message.getSenderId() == null) {
      throw new BadRequestException(
          "Choose a Broadcast sender before launching this broadcast");
    }

    BroadcastSender sender = senderService.requireActive(companyId, message.getSenderId());
    BroadcastSenderConnection senderConnection =
        senderService.requireConnection(companyId, sender.getConnectionId());
    if (!sender.getProvider().supports(group.getChannelType())
        || senderConnection.getProvider() != sender.getProvider()) {
      throw new BadRequestException("Broadcast sender provider is not supported or mismatched");
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
    run.setBodyHtmlSnapshot(message.getBodyHtml());
    run.setTemplateVariablesJsonSnapshot(message.getTemplateVariablesJson());

    if (sender.getProvider() == BroadcastSenderProvider.MSG91_EMAIL) {
      if (message.getSourceTemplateId() == null || message.getSourceTemplateVersion() == null) {
        throw new BadRequestException(
            "MSG91 Email requires a published template synced to the selected connection");
      }
      EmailTemplateProviderBinding binding =
          templateProviderBindingService.requireSendableForBroadcast(
              companyId,
              message.getSourceTemplateId(),
              message.getSourceTemplateVersion(),
              senderConnection.getId());
      run.setProviderTemplateBindingId(binding.getId());
      run.setProviderTemplateIdSnapshot(binding.getProviderTemplateId());
    }

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
