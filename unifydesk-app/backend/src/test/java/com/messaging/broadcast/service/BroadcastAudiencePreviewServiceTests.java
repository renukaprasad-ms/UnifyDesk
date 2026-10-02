package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.dto.BroadcastAudiencePreviewRequest;
import com.messaging.broadcast.dto.BroadcastAudiencePreviewResponse;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.Capabilities;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.SenderReadiness;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.SenderState;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.channel.enums.ChannelType;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.audience.ContactAudienceSelectionService;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.EligibilityDecision;
import com.messaging.contacts.consent.ConsentTypes.EligibilityReason;
import com.messaging.contacts.consent.ConsentTypes.EligibilityResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BroadcastAudiencePreviewServiceTests {

  @Test
  void directContactsPreviewKeepsExactDuplicateAndEligibilityAccounting() {
    AudienceSourceResolver sourceResolver = mock(AudienceSourceResolver.class);
    ContactAudienceSelectionService selections = mock(ContactAudienceSelectionService.class);
    ContactEligibilityService eligibility = mock(ContactEligibilityService.class);
    BroadcastSenderService senders = mock(BroadcastSenderService.class);
    BroadcastChannelReadinessService readiness = mock(BroadcastChannelReadinessService.class);
    BroadcastAudiencePreviewService service =
        new BroadcastAudiencePreviewService(
            sourceResolver, selections, eligibility, senders, readiness);

    BroadcastSender sender = new BroadcastSender();
    sender.setId(55L);
    sender.setProvider(BroadcastSenderProvider.META_WHATSAPP);
    when(senders.requireActive(7L, 55L)).thenReturn(sender);
    when(selections.validate(7L, List.of(10L, 10L, 11L)))
        .thenReturn(
            new ContactAudienceSelectionService.PreviewSelection(
                List.of(10L, 11L), 1L, "selection-hash"));

    when(eligibility.evaluateBatch(
            eq(7L),
            eq(List.of(10L, 11L)),
            eq(Channel.WHATSAPP),
            any(),
            any()))
        .thenReturn(
            Map.of(
                10L,
                new EligibilityResult(
                    EligibilityDecision.ELIGIBLE,
                    EligibilityReason.ELIGIBLE,
                    100L,
                    "+919876543210",
                    "v1",
                    Instant.now()),
                11L,
                new EligibilityResult(
                    EligibilityDecision.INELIGIBLE,
                    EligibilityReason.OPTED_OUT,
                    101L,
                    "+919999999999",
                    "v1",
                    Instant.now())));

    when(readiness.sender(7L, 55L))
        .thenReturn(
            new SenderReadiness(
                "55",
                "WhatsApp",
                "+919800000000",
                SenderState.READY,
                null,
                new Capabilities(true, true, true, true, true, true)));

    BroadcastAudiencePreviewResponse result =
        service.preview(
            7L,
            new BroadcastAudiencePreviewRequest(
                BroadcastAudienceSourceType.CONTACTS,
                null,
                List.of(10L, 10L, 11L),
                ChannelType.WHATSAPP,
                55L));

    assertThat(result.selectedCount()).isEqualTo(3);
    assertThat(result.eligibleCount()).isEqualTo(1);
    assertThat(result.excludedCount()).isEqualTo(2);
    assertThat(result.exclusions().get(BroadcastAudiencePreviewResponse.ExclusionReason.DUPLICATE))
        .isEqualTo(1);
    assertThat(result.exclusions().get(BroadcastAudiencePreviewResponse.ExclusionReason.OPTED_OUT))
        .isEqualTo(1);
    assertThat(result.selectedCount())
        .isEqualTo(result.eligibleCount() + result.excludedCount());
    assertThat(result.sampleEligible().getFirst().maskedDestination())
        .doesNotContain("9876543210");
    assertThat(result.sourceDescriptor().sourceVersion()).isEqualTo("selection-hash");
    assertThat(result.sourceDescriptor().contactSelectionHash()).isEqualTo("selection-hash");
  }
  @Test
  void segmentPreviewReturnsAuthoritativeFilterVersionAndHash() {
    AudienceSourceResolver sourceResolver = mock(AudienceSourceResolver.class);
    ContactAudienceSelectionService selections = mock(ContactAudienceSelectionService.class);
    ContactEligibilityService eligibility = mock(ContactEligibilityService.class);
    BroadcastSenderService senders = mock(BroadcastSenderService.class);
    BroadcastChannelReadinessService readiness = mock(BroadcastChannelReadinessService.class);
    BroadcastAudiencePreviewService service =
        new BroadcastAudiencePreviewService(
            sourceResolver, selections, eligibility, senders, readiness);

    BroadcastSender sender = new BroadcastSender();
    sender.setId(55L);
    sender.setProvider(BroadcastSenderProvider.AMAZON_SES);
    when(senders.requireActive(7L, 55L)).thenReturn(sender);
    AudienceSourceResolver.SourceDescriptor source =
        new AudienceSourceResolver.SourceDescriptor(
            AudienceSourceResolver.SourceType.SEGMENT, 77L, "9", "filter-hash");
    when(sourceResolver.describeForBroadcast(7L, AudienceSourceResolver.SourceType.SEGMENT, 77L))
        .thenReturn(new AudienceSourceResolver.BroadcastSourceState(source, 3, null));
    when(sourceResolver.page(7L, source, 0L, 500)).thenReturn(List.of());
    when(readiness.sender(7L, 55L))
        .thenReturn(
            new SenderReadiness(
                "55",
                "Email",
                "sender@example.com",
                SenderState.READY,
                null,
                new Capabilities(true, true, false, false, false, false)));

    BroadcastAudiencePreviewResponse result =
        service.preview(
            7L,
            new BroadcastAudiencePreviewRequest(
                BroadcastAudienceSourceType.SEGMENT,
                77L,
                null,
                ChannelType.EMAIL,
                55L));

    assertThat(result.sourceDescriptor().sourceId()).isEqualTo("77");
    assertThat(result.sourceDescriptor().sourceVersion()).isEqualTo("9");
    assertThat(result.sourceDescriptor().filterVersion()).isEqualTo(3);
    assertThat(result.sourceDescriptor().filterHash()).isEqualTo("filter-hash");
  }

}
