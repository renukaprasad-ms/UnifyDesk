package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import com.messaging.emailtemplate.service.EmailTemplateProviderBindingService;
import com.messaging.managedemail.delivery.ManagedEmailLaunchGuard;
import com.messaging.managedsms.delivery.ManagedSmsLaunchGuard;
import com.messaging.managedsms.runtime.ManagedSmsRunBindingService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
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
  private final ManagedEmailLaunchGuard managedEmailLaunchGuard;
  private final ManagedSmsLaunchGuard managedSmsLaunchGuard;
  private final ManagedSmsRunBindingService managedSmsRunBindingService;
  private final BroadcastAudienceFreezeService audienceFreezeService;
  private final BroadcastWhatsAppTemplateService whatsAppTemplateService;

  @Transactional
  public BroadcastRun launch(Long companyId, Long broadcastId) {
    return launchInternal(companyId, broadcastId, false);
  }

  @Transactional
  public BroadcastRun launchClaimed(Long companyId, Long broadcastId) {
    return launchInternal(companyId, broadcastId, true);
  }

  private BroadcastRun launchInternal(Long companyId, Long broadcastId, boolean allowClaimed) {
    BroadcastMessage message =
        messageRepository
            .findByIdAndCompanyId(broadcastId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));

    BroadcastRun existing = runRepository.findByBroadcastMessageId(broadcastId).orElse(null);
    if (existing != null) return existing;

    if (!(allowClaimed && message.getStatus() == BroadcastMessageStatus.QUEUED)) {
      stateMachine.requireSendable(message.getStatus());
    }

    ChannelType channelType = message.getChannelType();
    if (message.getSenderId() == null) {
      throw new BadRequestException("Choose a Broadcast sender before launching this broadcast");
    }

    Long maxMemberId = null;
    long recipientCount;
    AudienceSnapshotRepository.Snapshot audienceSnapshot = null;

    if (message.getGroupId() != null) {
      BroadcastGroup group =
          groupRepository
              .findByIdAndCompanyId(message.getGroupId(), companyId)
              .orElseThrow(() -> new NotFoundException("Broadcast group not found"));
      if (group.getChannelType() != channelType) {
        throw new BadRequestException("Broadcast group and message channel do not match");
      }
      maxMemberId = memberRepository.findMaxActiveMemberId(group.getId());
      if (maxMemberId == null) {
        throw new BadRequestException("Broadcast group has no active recipients");
      }
      recipientCount = memberRepository.countActiveMembers(group.getId());
    } else {
      if (message.getAudienceSourceType() == null || message.getAudienceSourceId() == null) {
        throw new BadRequestException("Broadcast audience source is missing");
      }
      BroadcastAudienceFreezeService.FrozenAudience frozen =
          audienceFreezeService.freeze(message);
      audienceSnapshot = frozen.snapshot();
      recipientCount = frozen.candidateCount();
      message.setFrozenAudienceSnapshotId(audienceSnapshot.id());
      if (recipientCount <= 0) {
        throw new BadRequestException("Broadcast audience has no active recipients");
      }
    }

    BroadcastSender sender = senderService.requireActive(companyId, message.getSenderId());
    BroadcastSenderConnection senderConnection =
        senderService.requireConnection(companyId, sender.getConnectionId());
    if (!sender.getProvider().supports(channelType)
        || senderConnection.getProvider() != sender.getProvider()) {
      throw new BadRequestException("Broadcast sender provider is not supported or mismatched");
    }

    BroadcastWhatsAppTemplateService.Prepared whatsAppTemplate = null;
    if (sender.getProvider() == BroadcastSenderProvider.META_WHATSAPP) {
      if (message.getWhatsAppTemplateId() == null
          || senderConnection.getChannelConnectionId() == null) {
        throw new BadRequestException("WHATSAPP_TEMPLATE_NOT_READY");
      }
      whatsAppTemplate =
          whatsAppTemplateService.validate(
              companyId,
              senderConnection.getChannelConnectionId(),
              message.getWhatsAppTemplateId(),
              whatsAppTemplateService.readParameters(
                  message.getWhatsAppTemplateParametersJson()));
    }

    ManagedSmsRuntimeRepository.ManagedTemplateRuntime managedSmsRuntime = null;
    if (senderConnection.getManagementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED) {
      if (sender.getProvider() == BroadcastSenderProvider.AMAZON_SES) {
        managedEmailLaunchGuard.validateLaunch(companyId, sender, senderConnection, recipientCount);
      } else if (sender.getProvider() == BroadcastSenderProvider.MSG91_SMS) {
        managedSmsRuntime =
            managedSmsLaunchGuard.validateLaunch(
                companyId, message, sender, senderConnection, recipientCount);
      } else {
        throw new BadRequestException("Managed Broadcast provider is not supported");
      }
    }

    BroadcastRun run = new BroadcastRun();
    run.setBroadcastMessageId(message.getId());
    run.setCompanyId(companyId);
    run.setGroupId(message.getGroupId());
    run.setAudienceSourceType(message.getAudienceSourceType());
    run.setAudienceSourceId(message.getAudienceSourceId());
    run.setAudienceSnapshotId(audienceSnapshot == null ? null : audienceSnapshot.id());
    run.setSenderId(sender.getId());
    run.setProviderType(sender.getProvider());
    run.setStatus(BroadcastRunStatus.PREPARING);
    run.setSubjectSnapshot(message.getSubject());
    run.setBodyTextSnapshot(message.getBodyText());
    run.setBodyHtmlSnapshot(message.getBodyHtml());
    run.setTemplateVariablesJsonSnapshot(message.getTemplateVariablesJson());
    if (whatsAppTemplate != null) {
      run.setWhatsAppTemplateIdSnapshot(message.getWhatsAppTemplateId());
      run.setWhatsAppTemplateNameSnapshot(whatsAppTemplate.name());
      run.setWhatsAppTemplateLanguageSnapshot(whatsAppTemplate.language());
      run.setWhatsAppTemplateComponentsJsonSnapshot(
          whatsAppTemplate.providerComponentsJson());
    }

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

    if (managedSmsRuntime != null) {
      managedSmsRunBindingService.bind(run.getId(), managedSmsRuntime);
    }

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
