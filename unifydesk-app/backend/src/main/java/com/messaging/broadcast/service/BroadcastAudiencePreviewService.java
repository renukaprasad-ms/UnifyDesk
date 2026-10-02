package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastAudiencePreviewRequest;
import com.messaging.broadcast.dto.BroadcastAudiencePreviewResponse;
import com.messaging.broadcast.dto.BroadcastAudiencePreviewResponse.ExclusionReason;
import com.messaging.broadcast.dto.BroadcastAudiencePreviewResponse.RecipientSample;
import com.messaging.broadcast.dto.BroadcastExpectedSourceDescriptor;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.audience.ContactAudienceSelectionService;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.EligibilityContext;
import com.messaging.contacts.consent.ConsentTypes.EligibilityDecision;
import com.messaging.contacts.consent.ConsentTypes.EligibilityReason;
import com.messaging.contacts.consent.ConsentTypes.EligibilityResult;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastAudiencePreviewService {

  private static final int BATCH_SIZE = 500;
  private static final int SAMPLE_SIZE = 10;

  private final AudienceSourceResolver sourceResolver;
  private final ContactAudienceSelectionService selectionService;
  private final ContactEligibilityService eligibilityService;
  private final BroadcastSenderService senderService;
  private final BroadcastChannelReadinessService readinessService;

  public BroadcastAudiencePreviewResponse preview(
      long companyId, BroadcastAudiencePreviewRequest request) {
    BroadcastSender sender = senderService.requireActive(companyId, request.senderId());
    if (!sender.getProvider().supports(request.channelType())) {
      throw new BadRequestException("Selected Broadcast sender does not support the channel");
    }

    EnumMap<ExclusionReason, Long> exclusions = new EnumMap<>(ExclusionReason.class);
    for (ExclusionReason reason : ExclusionReason.values()) exclusions.put(reason, 0L);
    List<RecipientSample> sampleEligible = new ArrayList<>();
    List<RecipientSample> sampleExcluded = new ArrayList<>();

    long selected = 0;
    long eligible = 0;
    AudienceSourceResolver.SourceDescriptor descriptor;
    Integer filterVersion = null;
    String contactSelectionHash = null;

    if (request.sourceType() == com.messaging.broadcast.enums.BroadcastAudienceSourceType.CONTACTS) {
      if (request.sourceId() != null) {
        throw new BadRequestException("CONTACT_SELECTION_INVALID");
      }
      ContactAudienceSelectionService.PreviewSelection selection =
          selectionService.validate(companyId, request.contactIds());
      contactSelectionHash = selection.selectionHash();
      exclusions.put(ExclusionReason.DUPLICATE, selection.duplicateCount());
      selected += selection.duplicateCount();
      descriptor =
          new AudienceSourceResolver.SourceDescriptor(
              AudienceSourceResolver.SourceType.CONTACTS,
              0L,
              selection.selectionHash(),
              selection.selectionHash());
      for (int from = 0; from < selection.contactIds().size(); from += BATCH_SIZE) {
        List<Long> batch =
            selection.contactIds().subList(
                from, Math.min(selection.contactIds().size(), from + BATCH_SIZE));
        ResolutionCounts counts =
            evaluate(
                companyId,
                sender,
                request,
                batch,
                exclusions,
                sampleEligible,
                sampleExcluded);
        selected += counts.selected();
        eligible += counts.eligible();
      }
    } else {
      if (request.sourceId() == null
          || (request.contactIds() != null && !request.contactIds().isEmpty())) {
        throw new BadRequestException("Broadcast audience source is invalid");
      }
      AudienceSourceResolver.SourceType type =
          AudienceSourceResolver.SourceType.valueOf(request.sourceType().name());
      AudienceSourceResolver.BroadcastSourceState sourceState =
          sourceResolver.describeForBroadcast(companyId, type, request.sourceId());
      descriptor = sourceState.source();
      filterVersion = sourceState.filterVersion();
      long cursor = 0;
      while (true) {
        List<Long> batch = sourceResolver.page(companyId, descriptor, cursor, BATCH_SIZE);
        if (batch.isEmpty()) break;
        ResolutionCounts counts =
            evaluate(
                companyId,
                sender,
                request,
                batch,
                exclusions,
                sampleEligible,
                sampleExcluded);
        selected += counts.selected();
        eligible += counts.eligible();
        cursor = batch.getLast();
      }
    }

    long excluded = selected - eligible;
    long reasonTotal = exclusions.values().stream().mapToLong(Long::longValue).sum();
    if (reasonTotal != excluded) {
      throw new IllegalStateException("Audience preview accounting invariant violated");
    }

    Instant now = Instant.now();
    return new BroadcastAudiencePreviewResponse(
        request.channelType(),
        Purpose.MARKETING.name(),
        new BroadcastExpectedSourceDescriptor(
            request.sourceType(),
            request.sourceType()
                    == com.messaging.broadcast.enums.BroadcastAudienceSourceType.CONTACTS
                ? null
                : Long.toString(descriptor.id()),
            descriptor.sourceVersion(),
            filterVersion,
            request.sourceType()
                    == com.messaging.broadcast.enums.BroadcastAudienceSourceType.SEGMENT
                ? descriptor.filterHash()
                : null,
            contactSelectionHash,
            now),
        selected,
        eligible,
        excluded,
        Map.copyOf(exclusions),
        List.copyOf(sampleEligible),
        List.copyOf(sampleExcluded),
        readinessService.sender(companyId, request.senderId()),
        now);
  }

  private ResolutionCounts evaluate(
      long companyId,
      BroadcastSender sender,
      BroadcastAudiencePreviewRequest request,
      List<Long> contactIds,
      EnumMap<ExclusionReason, Long> exclusions,
      List<RecipientSample> sampleEligible,
      List<RecipientSample> sampleExcluded) {
    Map<Long, EligibilityResult> decisions =
        eligibilityService.evaluateBatch(
            companyId,
            contactIds,
            Channel.valueOf(request.channelType().name()),
            Purpose.MARKETING,
            new EligibilityContext(sender.getProvider().name(), null, null));

    long eligible = 0;
    for (Long contactId : contactIds) {
      EligibilityResult result = decisions.get(contactId);
      if (result != null && result.decision() == EligibilityDecision.ELIGIBLE) {
        eligible++;
        if (sampleEligible.size() < SAMPLE_SIZE) {
          sampleEligible.add(
              new RecipientSample(
                  Long.toString(contactId),
                  mask(result.normalizedDestination()),
                  EligibilityReason.ELIGIBLE.name()));
        }
      } else {
        ExclusionReason reason = map(result == null ? null : result.reason());
        exclusions.merge(reason, 1L, Long::sum);
        if (sampleExcluded.size() < SAMPLE_SIZE) {
          sampleExcluded.add(
              new RecipientSample(
                  Long.toString(contactId),
                  mask(result == null ? null : result.normalizedDestination()),
                  reason.name()));
        }
      }
    }
    return new ResolutionCounts(contactIds.size(), eligible);
  }

  private ExclusionReason map(EligibilityReason reason) {
    if (reason == null) return ExclusionReason.CHANNEL_INELIGIBLE;
    return switch (reason) {
      case IDENTITY_MISSING, CONTACT_INACTIVE -> ExclusionReason.MISSING_IDENTITY;
      case IDENTITY_MISMATCH -> ExclusionReason.INVALID_IDENTITY;
      case OPTED_OUT -> ExclusionReason.OPTED_OUT;
      case CONSENT_REQUIRED -> ExclusionReason.CONSENT_REQUIRED;
      case SUPPRESSED -> ExclusionReason.SUPPRESSED;
      case POLICY_BLOCKED -> ExclusionReason.CHANNEL_INELIGIBLE;
      case ELIGIBLE -> throw new IllegalStateException("Eligible result cannot be excluded");
    };
  }

  private String mask(String destination) {
    if (destination == null || destination.isBlank()) return null;
    int at = destination.indexOf('@');
    if (at > 0 && at < destination.length() - 1) {
      return destination.substring(0, 1) + "***@" + destination.substring(at + 1);
    }
    String digits = destination.replaceAll("\\D", "");
    if (digits.length() >= 4) return "***" + digits.substring(digits.length() - 4);
    return "***";
  }

  private record ResolutionCounts(long selected, long eligible) {}
}
