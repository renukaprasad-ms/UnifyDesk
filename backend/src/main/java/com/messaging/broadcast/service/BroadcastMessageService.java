package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastMessageService {

  private final BroadcastGroupService groupService;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastStatsRepository statsRepository;
  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastSenderService senderService;
  private final BroadcastStateMachine stateMachine;
  private final BroadcastLaunchService launchService;

  @Transactional
  public BroadcastMessageResponse create(
      Long companyId,
      Long userId,
      Long groupId,
      CreateBroadcastMessageRequest request) {
    BroadcastGroup group = groupService.require(companyId, groupId);
    validateSender(companyId, group, request.senderId());

    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(companyId);
    message.setGroupId(groupId);
    message.setSenderId(request.senderId());
    message.setChannelType(group.getChannelType());
    message.setSubject(trimToNull(request.subject()));
    message.setBodyText(request.bodyText());
    message.setCreatedByUserId(userId);
    message.setStatus(BroadcastMessageStatus.DRAFT);
    return response(messageRepository.save(message));
  }

  @Transactional
  public BroadcastMessageResponse update(
      Long companyId, Long broadcastId, UpdateBroadcastMessageRequest request) {
    BroadcastMessage message = require(companyId, broadcastId);
    stateMachine.requireEditable(message.getStatus());
    BroadcastGroup group = groupService.require(companyId, message.getGroupId());
    validateSender(companyId, group, request.senderId());
    message.setSenderId(request.senderId());
    message.setConnectionId(null);
    message.setSubject(trimToNull(request.subject()));
    message.setBodyText(request.bodyText());
    return response(messageRepository.save(message));
  }

  @Transactional
  public BroadcastMessageResponse schedule(
      Long companyId, Long broadcastId, ScheduleBroadcastRequest request) {
    BroadcastMessage message = require(companyId, broadcastId);
    stateMachine.requireEditable(message.getStatus());
    requireModernSender(companyId, message);
    if (!request.scheduledAt().isAfter(Instant.now())) {
      throw new BadRequestException("Broadcast schedule time must be in the future");
    }
    message.setScheduledAt(request.scheduledAt());
    message.setStatus(BroadcastMessageStatus.SCHEDULED);
    return response(messageRepository.save(message));
  }

  @Transactional
  public BroadcastMessageResponse sendNow(Long companyId, Long broadcastId) {
    BroadcastMessage message = require(companyId, broadcastId);
    requireModernSender(companyId, message);
    launchService.launch(companyId, broadcastId);
    return response(require(companyId, broadcastId));
  }

  @Transactional(readOnly = true)
  public Page<BroadcastMessageSummaryResponse> list(
      Long companyId, Long groupId, Pageable pageable) {
    groupService.require(companyId, groupId);
    return messageRepository
        .findByGroupIdOrderByCreatedAtDesc(groupId, pageable)
        .map(this::summary);
  }

  @Transactional(readOnly = true)
  public BroadcastMessageResponse get(Long companyId, Long broadcastId) {
    return response(require(companyId, broadcastId));
  }

  @Transactional(readOnly = true)
  public Page<BroadcastRecipientResponse> recipients(
      Long companyId, Long broadcastId, Pageable pageable) {
    BroadcastMessage message = require(companyId, broadcastId);
    BroadcastRun run =
        runRepository
            .findByBroadcastMessageId(message.getId())
            .orElseThrow(() -> new NotFoundException("Broadcast has not started"));
    return recipientRepository
        .findByRunIdOrderByCreatedAtAsc(run.getId(), pageable)
        .map(BroadcastRecipientResponse::from);
  }

  public BroadcastMessage require(Long companyId, Long broadcastId) {
    return messageRepository
        .findByIdAndCompanyId(broadcastId, companyId)
        .orElseThrow(() -> new NotFoundException("Broadcast not found"));
  }

  private void validateSender(Long companyId, BroadcastGroup group, Long senderId) {
    if (group.getChannelType() != ChannelType.EMAIL) {
      throw new BadRequestException("Amazon SES senders are only available for email broadcasts");
    }
    BroadcastSender sender = senderService.requireActive(companyId, senderId);
    if (sender.getProvider() != com.messaging.broadcast.enums.BroadcastSenderProvider.AMAZON_SES) {
      throw new BadRequestException("Amazon SES is currently required for email Broadcast");
    }
  }

  private void requireModernSender(Long companyId, BroadcastMessage message) {
    if (message.getSenderId() == null) {
      throw new BadRequestException(
          "This legacy draft uses a mailbox sender. Choose an Amazon SES sender before sending.");
    }
    senderService.requireActive(companyId, message.getSenderId());
  }

  private BroadcastMessageResponse response(BroadcastMessage message) {
    BroadcastRun run = runRepository.findByBroadcastMessageId(message.getId()).orElse(null);
    BroadcastStats stats =
        run == null ? null : statsRepository.findByRunId(run.getId()).orElse(null);
    return BroadcastMessageResponse.from(message, run, BroadcastStatsResponse.from(stats));
  }

  private BroadcastMessageSummaryResponse summary(BroadcastMessage message) {
    BroadcastRun run = runRepository.findByBroadcastMessageId(message.getId()).orElse(null);
    BroadcastStats stats =
        run == null ? null : statsRepository.findByRunId(run.getId()).orElse(null);
    return BroadcastMessageSummaryResponse.from(
        message, run, BroadcastStatsResponse.from(stats));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
