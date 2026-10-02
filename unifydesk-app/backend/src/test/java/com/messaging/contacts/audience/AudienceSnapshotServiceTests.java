package com.messaging.contacts.audience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobStatus;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.AudienceSourceResolver.SourceDescriptor;
import com.messaging.contacts.audience.AudienceSourceResolver.SourceType;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.contacts.consent.ConsentTypes.*;
import com.messaging.contacts.integration.ContactCoreReadPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AudienceSnapshotServiceTests {

  private AudienceSourceResolver sourceResolver;
  private AudienceSnapshotRepository repository;
  private ContactEligibilityService eligibilityService;
  private ContactAudienceProperties properties;
  private AsyncJobService asyncJobs;
  private AudienceSnapshotService service;

  @BeforeEach
  void setUp() {
    sourceResolver = mock(AudienceSourceResolver.class);
    repository = mock(AudienceSnapshotRepository.class);
    eligibilityService = mock(ContactEligibilityService.class);
    properties = new ContactAudienceProperties();
    asyncJobs = mock(AsyncJobService.class);
    service =
        new AudienceSnapshotService(
            sourceResolver,
            repository,
            mock(ContactCoreReadPort.class),
            eligibilityService,
            properties,
            asyncJobs);
  }

  @Test
  void normalAsyncListSnapshotCapturesCandidatesAndProcessesRecipients() {
    long companyId = 7L;
    long snapshotId = 101L;
    SourceDescriptor source = new SourceDescriptor(SourceType.LIST, 11L, "3", null);
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(snapshotId, companyId, SourceType.LIST, 11L, "3", null, 501L);

    stubAsyncCreate(companyId, 501L);
    when(sourceResolver.describe(companyId, SourceType.LIST, 11L)).thenReturn(source);
    when(repository.create(
            anyLong(),
            eq(companyId),
            eq("LIST"),
            eq(11L),
            eq("3"),
            isNull(),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            eq(501L)))
        .thenReturn(snapshot);
    when(repository.requireForUpdate(companyId, snapshotId)).thenReturn(snapshot);
    when(repository.pageCandidates(companyId, snapshotId, 0L, properties.boundedBatchSize()))
        .thenReturn(List.of(21L, 22L));
    when(eligibilityService.evaluateBatch(
            eq(companyId),
            eq(List.of(21L, 22L)),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            any(EligibilityContext.class)))
        .thenReturn(eligible(21L, 22L));
    when(repository.insertMembers(eq(snapshotId), eq(companyId), anyList())).thenReturn(2);

    AudienceSnapshotService.SnapshotJob created =
        service.create(
            companyId, 9L, SourceType.LIST, 11L, Channel.EMAIL, Purpose.MARKETING);
    boolean more = service.processBatch(companyId, snapshotId);

    assertThat(created.snapshot().id()).isEqualTo(snapshotId);
    assertThat(more).isTrue();
    verify(sourceResolver).captureCandidates(companyId, source, snapshotId);
    verify(repository)
        .advance(companyId, snapshotId, 0L, 22L, 2L, 2L);
  }

  @Test
  void normalAsyncSegmentSnapshotCapturesCandidatesAndProcessesRecipients() {
    long companyId = 7L;
    long snapshotId = 102L;
    SourceDescriptor source =
        new SourceDescriptor(SourceType.SEGMENT, 12L, "8", "segment-filter-hash");
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(
            snapshotId,
            companyId,
            SourceType.SEGMENT,
            12L,
            "8",
            "segment-filter-hash",
            502L);

    stubAsyncCreate(companyId, 502L);
    when(sourceResolver.describe(companyId, SourceType.SEGMENT, 12L)).thenReturn(source);
    when(repository.create(
            anyLong(),
            eq(companyId),
            eq("SEGMENT"),
            eq(12L),
            eq("8"),
            eq("segment-filter-hash"),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            eq(502L)))
        .thenReturn(snapshot);
    when(repository.requireForUpdate(companyId, snapshotId)).thenReturn(snapshot);
    when(repository.pageCandidates(companyId, snapshotId, 0L, properties.boundedBatchSize()))
        .thenReturn(List.of(31L));
    when(eligibilityService.evaluateBatch(
            eq(companyId),
            eq(List.of(31L)),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            any(EligibilityContext.class)))
        .thenReturn(eligible(31L));
    when(repository.insertMembers(eq(snapshotId), eq(companyId), anyList())).thenReturn(1);

    service.create(
        companyId, 9L, SourceType.SEGMENT, 12L, Channel.EMAIL, Purpose.MARKETING);
    boolean more = service.processBatch(companyId, snapshotId);

    assertThat(more).isTrue();
    verify(sourceResolver).captureCandidates(companyId, source, snapshotId);
    verify(repository)
        .advance(companyId, snapshotId, 0L, 31L, 1L, 1L);
  }

  @Test
  void broadcastSnapshotProcessesOnlyCapturedCandidateIds() {
    long companyId = 7L;
    long snapshotId = 201L;
    SourceDescriptor source = new SourceDescriptor(SourceType.LIST, 41L, "5", null);
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(snapshotId, companyId, SourceType.LIST, 41L, "5", null, null);

    BroadcastSourceState sourceState = new BroadcastSourceState(source, null, null);
    when(sourceResolver.describeForBroadcast(companyId, SourceType.LIST, 41L))
        .thenReturn(sourceState);
    when(repository.create(
            anyLong(),
            eq(companyId),
            eq("LIST"),
            eq(41L),
            eq("5"),
            isNull(),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            isNull()))
        .thenReturn(snapshot);
    when(repository.requireForUpdate(companyId, snapshotId)).thenReturn(snapshot);
    when(repository.pageCandidates(companyId, snapshotId, 0L, properties.boundedBatchSize()))
        .thenReturn(List.of(51L, 53L));
    when(eligibilityService.evaluateBatch(
            eq(companyId),
            eq(List.of(51L, 53L)),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            any(EligibilityContext.class)))
        .thenReturn(eligible(51L, 53L));
    when(repository.insertMembers(eq(snapshotId), eq(companyId), anyList())).thenReturn(2);

    service.createForBroadcast(
        companyId, SourceType.LIST, 41L, Channel.EMAIL, Purpose.MARKETING);
    service.processBatch(companyId, snapshotId);

    verify(sourceResolver).captureCandidates(companyId, sourceState, snapshotId);
    verify(sourceResolver, never()).page(anyLong(), any(), anyLong(), anyInt());
    verify(eligibilityService)
        .evaluateBatch(
            eq(companyId),
            eq(List.of(51L, 53L)),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            any(EligibilityContext.class));
  }

  @Test
  void sourceChangesAfterBroadcastFreezeDoNotAlterCapturedMembership() {
    long companyId = 7L;
    long snapshotId = 202L;
    SourceDescriptor frozen = new SourceDescriptor(SourceType.LIST, 42L, "6", null);
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(snapshotId, companyId, SourceType.LIST, 42L, "6", null, null);

    BroadcastSourceState frozenState = new BroadcastSourceState(frozen, null, null);
    when(sourceResolver.describeForBroadcast(companyId, SourceType.LIST, 42L))
        .thenReturn(frozenState);
    when(repository.create(
            anyLong(),
            eq(companyId),
            eq("LIST"),
            eq(42L),
            eq("6"),
            isNull(),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            isNull()))
        .thenReturn(snapshot);
    when(repository.requireForUpdate(companyId, snapshotId)).thenReturn(snapshot);
    when(repository.pageCandidates(companyId, snapshotId, 0L, properties.boundedBatchSize()))
        .thenReturn(List.of(61L));
    when(eligibilityService.evaluateBatch(
            eq(companyId),
            eq(List.of(61L)),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            any(EligibilityContext.class)))
        .thenReturn(eligible(61L));
    when(repository.insertMembers(eq(snapshotId), eq(companyId), anyList())).thenReturn(1);

    service.createForBroadcast(
        companyId, SourceType.LIST, 42L, Channel.EMAIL, Purpose.MARKETING);
    service.processBatch(companyId, snapshotId);

    verify(eligibilityService)
        .evaluateBatch(
            eq(companyId),
            eq(List.of(61L)),
            eq(Channel.EMAIL),
            eq(Purpose.MARKETING),
            any(EligibilityContext.class));
    verify(sourceResolver, never()).page(anyLong(), any(), anyLong(), anyInt());
  }

  @Test
  void processingBatchSizeRemainsBoundedByHardMaximum() {
    long companyId = 7L;
    long snapshotId = 301L;
    properties.setProcessingBatchSize(10_000);
    properties.setProcessingBatchHardMax(2_000);
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(snapshotId, companyId, SourceType.LIST, 71L, "1", null, null);

    when(repository.requireForUpdate(companyId, snapshotId)).thenReturn(snapshot);
    when(repository.pageCandidates(companyId, snapshotId, 0L, 2_000)).thenReturn(List.of());

    boolean more = service.processBatch(companyId, snapshotId);

    assertThat(more).isFalse();
    verify(repository).pageCandidates(companyId, snapshotId, 0L, 2_000);
    verify(repository).ready(companyId, snapshotId);
  }

  private void stubAsyncCreate(long companyId, long jobId) {
    when(asyncJobs.create(
            eq(companyId),
            eq(9L),
            eq(AsyncJobType.CONTACT_AUDIENCE_SNAPSHOT),
            anyString(),
            isNull(),
            eq("Audience snapshot queued")))
        .thenReturn(
            new AsyncJobResponse(
                Long.toString(jobId),
                Long.toString(companyId),
                "9",
                AsyncJobType.CONTACT_AUDIENCE_SNAPSHOT,
                AsyncJobStatus.QUEUED,
                0,
                0,
                0,
                "Audience snapshot queued",
                null,
                Instant.now(),
                null,
                null,
                Instant.now()));
  }

  private AudienceSnapshotRepository.Snapshot snapshot(
      long snapshotId,
      long companyId,
      SourceType sourceType,
      long sourceId,
      String sourceVersion,
      String filterHash,
      Long asyncJobId) {
    return new AudienceSnapshotRepository.Snapshot(
        snapshotId,
        companyId,
        sourceType.name(),
        sourceId,
        sourceVersion,
        filterHash,
        Channel.EMAIL,
        Purpose.MARKETING,
        "PREPARING",
        0,
        0,
        0,
        asyncJobId,
        Instant.now(),
        null);
  }

  private Map<Long, EligibilityResult> eligible(Long... contactIds) {
    Map<Long, EligibilityResult> results = new LinkedHashMap<>();
    for (Long contactId : contactIds) {
      results.put(
          contactId,
          new EligibilityResult(
              EligibilityDecision.ELIGIBLE,
              EligibilityReason.ELIGIBLE,
              contactId + 1_000,
              "user" + contactId + "@example.com",
              "policy-v1",
              Instant.now()));
    }
    return results;
  }
}
