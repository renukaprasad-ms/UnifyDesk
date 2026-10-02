package com.messaging.broadcast.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.audience.ContactAudienceSelectionService;
import com.messaging.emailtemplate.enums.EmailTemplatePurpose;
import com.messaging.emailtemplate.service.EmailTemplateService;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import java.time.Instant;
import java.util.*;
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
  private final EmailTemplateService emailTemplateService;
  private final ManagedSmsTemplateService managedSmsTemplateService;
  private final AudienceSourceResolver audienceSourceResolver;
  private final ContactAudienceSelectionService contactAudienceSelectionService;
  private final BroadcastAudienceFreezeService audienceFreezeService;
  private final BroadcastWhatsAppTemplateService whatsAppTemplateService;
  private final ObjectMapper objectMapper;

  @Transactional
  public BroadcastMessageResponse create(
      Long companyId, Long userId, Long groupId, CreateBroadcastMessageRequest request) {
    BroadcastGroup group = groupService.require(companyId, groupId);
    BroadcastSender sender = validateSender(companyId, group, request.senderId());
    BroadcastSenderConnection connection =
        senderService.requireConnection(companyId, sender.getConnectionId());

    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(companyId);
    message.setGroupId(groupId);
    message.setSenderId(request.senderId());
    message.setChannelType(group.getChannelType());
    applyContent(
        companyId,
        message.getChannelType(),
        request.templateId(),
        request.managedSmsTemplateId(),
        request.whatsappTemplateId(),
        request.whatsappTemplateParameters(),
        request.subject(),
        request.bodyText(),
        request.bodyHtml(),
        request.templateVariables(),
        sender,
        connection,
        message);
    message.setCreatedByUserId(userId);
    message.setStatus(BroadcastMessageStatus.DRAFT);
    return response(messageRepository.save(message));
  }

  @Transactional
  public BroadcastMessageResponse createAudience(
      Long companyId, Long userId, CreateAudienceBroadcastMessageRequest request) {
    Long audienceSourceId;
    if (request.sourceType() == com.messaging.broadcast.enums.BroadcastAudienceSourceType.CONTACTS) {
      if (request.sourceId() != null) {
        throw new BadRequestException("CONTACT_SELECTION_INVALID");
      }
      ContactAudienceSelectionService.Selection selection =
          contactAudienceSelectionService.create(companyId, userId, request.contactIds());
      audienceSourceId = selection.id();
    } else {
      if (request.sourceId() == null
          || (request.contactIds() != null && !request.contactIds().isEmpty())) {
        throw new BadRequestException("Broadcast audience source is invalid");
      }
      AudienceSourceResolver.SourceType sourceType =
          AudienceSourceResolver.SourceType.valueOf(request.sourceType().name());
      audienceSourceResolver.describe(companyId, sourceType, request.sourceId());
      audienceSourceId = request.sourceId();
    }

    BroadcastSender sender =
        validateSender(companyId, request.channelType(), request.senderId());
    BroadcastSenderConnection connection =
        senderService.requireConnection(companyId, sender.getConnectionId());

    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(companyId);
    message.setGroupId(null);
    message.setAudienceSourceType(request.sourceType());
    message.setAudienceSourceId(audienceSourceId);
    message.setSenderId(request.senderId());
    message.setChannelType(request.channelType());
    applyContent(
        companyId,
        request.channelType(),
        request.templateId(),
        request.managedSmsTemplateId(),
        request.whatsappTemplateId(),
        request.whatsappTemplateParameters(),
        request.subject(),
        request.bodyText(),
        request.bodyHtml(),
        request.templateVariables(),
        sender,
        connection,
        message);
    message.setCreatedByUserId(userId);
    message.setStatus(BroadcastMessageStatus.DRAFT);
    return response(messageRepository.save(message));
  }

  @Transactional
  public BroadcastMessageResponse update(
      Long companyId, Long broadcastId, UpdateBroadcastMessageRequest request) {
    BroadcastMessage message = require(companyId, broadcastId);
    stateMachine.requireEditable(message.getStatus());
    BroadcastSender sender =
        validateSender(companyId, message.getChannelType(), request.senderId());
    BroadcastSenderConnection connection =
        senderService.requireConnection(companyId, sender.getConnectionId());
    message.setSenderId(request.senderId());
    message.setConnectionId(null);
    applyContent(
        companyId,
        message.getChannelType(),
        request.templateId(),
        request.managedSmsTemplateId(),
        request.whatsappTemplateId(),
        request.whatsappTemplateParameters(),
        request.subject(),
        request.bodyText(),
        request.bodyHtml(),
        request.templateVariables(),
        sender,
        connection,
        message);
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
    freezeCanonicalAudience(message, request.expectedSourceDescriptor());
    message.setScheduledAt(request.scheduledAt());
    message.setStatus(BroadcastMessageStatus.SCHEDULED);
    return response(messageRepository.save(message));
  }

  @Transactional
  public BroadcastMessageResponse sendNow(Long companyId, Long broadcastId) {
    return sendNow(companyId, broadcastId, null);
  }

  @Transactional
  public BroadcastMessageResponse sendNow(
      Long companyId,
      Long broadcastId,
      BroadcastExpectedSourceDescriptor expectedSourceDescriptor) {
    BroadcastMessage message = require(companyId, broadcastId);
    requireModernSender(companyId, message);
    freezeCanonicalAudience(message, expectedSourceDescriptor);
    messageRepository.save(message);
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

  private void applyContent(
      Long companyId,
      ChannelType channelType,
      Long templateId,
      Long managedSmsTemplateId,
      Long whatsAppTemplateId,
      BroadcastWhatsAppTemplateParameters whatsAppTemplateParameters,
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables,
      BroadcastSender sender,
      BroadcastSenderConnection connection,
      BroadcastMessage message) {
    if (channelType == ChannelType.WHATSAPP) {
      if (sender.getProvider() != BroadcastSenderProvider.META_WHATSAPP
          || whatsAppTemplateId == null
          || templateId != null
          || managedSmsTemplateId != null
          || connection.getChannelConnectionId() == null) {
        throw new BadRequestException("WHATSAPP_TEMPLATE_NOT_READY");
      }
      BroadcastWhatsAppTemplateService.Prepared prepared =
          whatsAppTemplateService.validate(
              companyId,
              connection.getChannelConnectionId(),
              whatsAppTemplateId,
              whatsAppTemplateParameters);
      message.setSourceTemplateId(null);
      message.setSourceTemplateVersion(null);
      message.setWhatsAppTemplateId(whatsAppTemplateId);
      message.setWhatsAppTemplateParametersJson(
          whatsAppTemplateService.writeParameters(whatsAppTemplateParameters));
      message.setSubject(null);
      message.setBodyText("WhatsApp template: " + prepared.name());
      message.setBodyHtml(null);
      message.setTemplateVariablesJson("{}");
      return;
    }

    if (whatsAppTemplateId != null || whatsAppTemplateParameters != null) {
      throw new BadRequestException("WhatsApp templates require a Meta WhatsApp sender");
    }
    message.setWhatsAppTemplateId(null);
    message.setWhatsAppTemplateParametersJson("{}");

    if (connection.getManagementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED
        && sender.getProvider() == BroadcastSenderProvider.MSG91_SMS) {
      if (channelType != ChannelType.SMS || managedSmsTemplateId == null) {
        throw new BadRequestException("MANAGED_SMS_TEMPLATE_NOT_READY");
      }
      ManagedSmsTemplateService.DraftTemplate template =
          managedSmsTemplateService.renderForDraft(
              companyId, sender.getId(), managedSmsTemplateId, templateVariables);
      message.setSourceTemplateId(template.templateId());
      message.setSourceTemplateVersion(template.versionNo());
      message.setSubject(null);
      message.setBodyText(template.body());
      message.setBodyHtml(null);
      message.setTemplateVariablesJson(writeTemplateVariables(template.variables()));
      return;
    }

    if (managedSmsTemplateId != null) {
      throw new BadRequestException("Managed SMS templates require an UnifyDesk Managed SMS sender");
    }

    if (templateId != null) {
      if (channelType != ChannelType.EMAIL) {
        throw new BadRequestException("Reusable templates are Email-only in this phase");
      }
      EmailTemplateService.PublishedEmail template =
          emailTemplateService.requirePublishedForBroadcast(companyId, templateId);
      if (sender.getProvider() == BroadcastSenderProvider.MSG91_EMAIL
          && template.purpose() != EmailTemplatePurpose.TRANSACTIONAL) {
        throw new BadRequestException(
            "MSG91 Email only supports transactional Email templates");
      }
      message.setSourceTemplateId(template.templateId());
      message.setSourceTemplateVersion(template.version());
      message.setSubject(template.subject());
      message.setBodyText(template.bodyText());
      message.setBodyHtml(template.bodyHtml());
      message.setTemplateVariablesJson(
          writeTemplateVariables(normalizeTemplateVariables(templateVariables, template.variables())));
      return;
    }

    if (sender.getProvider() == BroadcastSenderProvider.MSG91_EMAIL) {
      throw new BadRequestException(
          "MSG91 Email requires a published Email template; scratch content is not supported");
    }

    String normalizedBody = bodyText == null ? "" : bodyText.trim();
    if (normalizedBody.isBlank()) {
      throw new BadRequestException("Broadcast message body is required");
    }
    message.setSourceTemplateId(null);
    message.setSourceTemplateVersion(null);
    message.setSubject(channelType == ChannelType.EMAIL ? trimToNull(subject) : null);
    message.setBodyText(normalizedBody);
    message.setBodyHtml(channelType == ChannelType.EMAIL ? trimToNull(bodyHtml) : null);
    message.setTemplateVariablesJson("{}");
  }

  private Map<String, String> normalizeTemplateVariables(
      Map<String, String> input, List<String> allowedVariables) {
    if (input == null || input.isEmpty() || allowedVariables == null || allowedVariables.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
    for (String variable : allowedVariables) {
      String value = trimToNull(input.get(variable));
      if (value == null) continue;
      if (value.length() > 10000) {
        throw new BadRequestException("Template variable value is too long: " + variable);
      }
      normalized.put(variable, value);
    }
    return Map.copyOf(normalized);
  }

  private String writeTemplateVariables(Map<String, String> values) {
    try {
      return objectMapper.writeValueAsString(values == null ? Map.of() : values);
    } catch (Exception exception) {
      throw new BadRequestException("Unable to save broadcast template variables");
    }
  }

  private Map<String, String> readTemplateVariables(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      Map<String, String> values =
          objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      return values == null ? Map.of() : Map.copyOf(values);
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private BroadcastSender validateSender(Long companyId, BroadcastGroup group, Long senderId) {
    return validateSender(companyId, group.getChannelType(), senderId);
  }

  private BroadcastSender validateSender(
      Long companyId, ChannelType channelType, Long senderId) {
    BroadcastSender sender = senderService.requireActive(companyId, senderId);
    if (!sender.getProvider().supports(channelType)) {
      throw new BadRequestException(
          "Selected Broadcast sender does not support " + channelType);
    }
    return sender;
  }

  private void freezeCanonicalAudience(
      BroadcastMessage message,
      BroadcastExpectedSourceDescriptor expectedSourceDescriptor) {
    if (message.getGroupId() != null) return;
    BroadcastAudienceFreezeService.FrozenAudience frozen =
        audienceFreezeService.freeze(message, expectedSourceDescriptor);
    if (frozen == null || frozen.candidateCount() <= 0) {
      throw new BadRequestException("Broadcast audience has no active recipients");
    }
    message.setFrozenAudienceSnapshotId(frozen.snapshot().id());
  }

  private void requireModernSender(Long companyId, BroadcastMessage message) {
    if (message.getSenderId() == null) {
      throw new BadRequestException(
          "This legacy draft uses a mailbox sender. Choose a Broadcast sender before sending.");
    }
    senderService.requireActive(companyId, message.getSenderId());
  }

  private BroadcastMessageResponse response(BroadcastMessage message) {
    BroadcastRun run = runRepository.findByBroadcastMessageId(message.getId()).orElse(null);
    BroadcastStats stats =
        run == null ? null : statsRepository.findByRunId(run.getId()).orElse(null);
    return BroadcastMessageResponse.from(
        message,
        run,
        BroadcastStatsResponse.from(stats),
        readTemplateVariables(message.getTemplateVariablesJson()),
        whatsAppTemplateService.readParameters(message.getWhatsAppTemplateParametersJson()));
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
