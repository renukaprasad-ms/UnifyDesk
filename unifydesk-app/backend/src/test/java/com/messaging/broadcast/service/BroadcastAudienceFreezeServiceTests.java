package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.dto.BroadcastExpectedSourceDescriptor;
import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.ConflictException;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.audience.AudienceSourceResolver.BroadcastSourceState;
import com.messaging.contacts.audience.AudienceSourceResolver.SourceDescriptor;
import com.messaging.contacts.audience.AudienceSourceResolver.SourceType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BroadcastAudienceFreezeServiceTests {

  @Test
  void unchangedListDescriptorFreezesValidatedSourceAndCapturesCandidates() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    AudienceSnapshotRepository.Snapshot snapshot = snapshot(300L, "LIST", 22L, "5", null);
    BroadcastSourceState state =
        new BroadcastSourceState(new SourceDescriptor(SourceType.LIST, 22L, "5", null), null, null);

    when(sources.describeForBroadcast(7L, SourceType.LIST, 22L)).thenReturn(state);
    when(snapshots.createForBroadcast(7L, state, Channel.EMAIL, Purpose.MARKETING))
        .thenReturn(snapshot);
    when(snapshots.candidateCount(7L, 300L)).thenReturn(42L);

    BroadcastAudienceFreezeService.FrozenAudience frozen =
        service.freeze(message(BroadcastAudienceSourceType.LIST, 22L), listExpected("5"));

    assertThat(frozen.snapshot().id()).isEqualTo(300L);
    assertThat(frozen.candidateCount()).isEqualTo(42L);
    verify(snapshots).createForBroadcast(7L, state, Channel.EMAIL, Purpose.MARKETING);
  }


  @Test
  void unchangedSegmentDescriptorFreezesAuthoritativeVersionAndFilter() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    BroadcastSourceState state =
        new BroadcastSourceState(
            new SourceDescriptor(SourceType.SEGMENT, 33L, "8", "filter-hash"), 3, null);
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(302L, "SEGMENT", 33L, "8", "filter-hash");
    when(sources.describeForBroadcast(7L, SourceType.SEGMENT, 33L)).thenReturn(state);
    when(snapshots.createForBroadcast(7L, state, Channel.EMAIL, Purpose.MARKETING))
        .thenReturn(snapshot);
    when(snapshots.candidateCount(7L, 302L)).thenReturn(12L);

    BroadcastExpectedSourceDescriptor expected =
        new BroadcastExpectedSourceDescriptor(
            BroadcastAudienceSourceType.SEGMENT,
            "33",
            "8",
            3,
            "filter-hash",
            null,
            Instant.now());

    assertThat(
            service.freeze(message(BroadcastAudienceSourceType.SEGMENT, 33L), expected)
                .candidateCount())
        .isEqualTo(12L);
    verify(snapshots).createForBroadcast(7L, state, Channel.EMAIL, Purpose.MARKETING);
  }

  @Test
  void sourceDriftDetectedDuringCandidateCaptureReturnsSameConflict() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    BroadcastSourceState state =
        new BroadcastSourceState(new SourceDescriptor(SourceType.LIST, 22L, "5", null), null, null);
    when(sources.describeForBroadcast(7L, SourceType.LIST, 22L)).thenReturn(state);
    when(snapshots.createForBroadcast(7L, state, Channel.EMAIL, Purpose.MARKETING))
        .thenThrow(new AudienceSourceResolver.SourceChangedException());

    assertThatThrownBy(
            () ->
                service.freeze(
                    message(BroadcastAudienceSourceType.LIST, 22L), listExpected("5")))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
  }

  @Test
  void staleListVersionIsRejectedBeforeSnapshotCreation() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    when(sources.describeForBroadcast(7L, SourceType.LIST, 22L))
        .thenReturn(
            new BroadcastSourceState(
                new SourceDescriptor(SourceType.LIST, 22L, "6", null), null, null));

    assertThatThrownBy(
            () ->
                service.freeze(
                    message(BroadcastAudienceSourceType.LIST, 22L), listExpected("5")))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
    verify(snapshots, never()).createForBroadcast(anyLong(), any(), any(), any());
  }

  @Test
  void staleSegmentVersionFilterVersionOrHashIsRejected() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    when(sources.describeForBroadcast(7L, SourceType.SEGMENT, 33L))
        .thenReturn(
            new BroadcastSourceState(
                new SourceDescriptor(SourceType.SEGMENT, 33L, "9", "new-hash"), 2, null));

    BroadcastExpectedSourceDescriptor expected =
        new BroadcastExpectedSourceDescriptor(
            BroadcastAudienceSourceType.SEGMENT,
            "33",
            "8",
            1,
            "old-hash",
            null,
            Instant.now());

    assertThatThrownBy(
            () -> service.freeze(message(BroadcastAudienceSourceType.SEGMENT, 33L), expected))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
    verify(snapshots, never()).createForBroadcast(anyLong(), any(), any(), any());
  }

  @Test
  void matchingContactSelectionHashFreezesAndMismatchIsRejected() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    String hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    BroadcastSourceState state =
        new BroadcastSourceState(
            new SourceDescriptor(SourceType.CONTACTS, 44L, hash, hash), null, hash);
    AudienceSnapshotRepository.Snapshot snapshot =
        snapshot(301L, "CONTACTS", 44L, hash, hash);
    when(sources.describeForBroadcast(7L, SourceType.CONTACTS, 44L)).thenReturn(state);
    when(snapshots.createForBroadcast(7L, state, Channel.EMAIL, Purpose.MARKETING))
        .thenReturn(snapshot);
    when(snapshots.candidateCount(7L, 301L)).thenReturn(2L);

    BroadcastExpectedSourceDescriptor matching =
        new BroadcastExpectedSourceDescriptor(
            BroadcastAudienceSourceType.CONTACTS,
            null,
            hash,
            null,
            null,
            hash,
            Instant.now());

    assertThat(
            service.freeze(message(BroadcastAudienceSourceType.CONTACTS, 44L), matching)
                .candidateCount())
        .isEqualTo(2L);

    BroadcastExpectedSourceDescriptor mismatched =
        new BroadcastExpectedSourceDescriptor(
            BroadcastAudienceSourceType.CONTACTS,
            null,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            null,
            null,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            Instant.now());
    BroadcastMessage second = message(BroadcastAudienceSourceType.CONTACTS, 44L);

    assertThatThrownBy(() -> service.freeze(second, mismatched))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
  }

  @Test
  void alreadyFrozenSnapshotIsReusedWithoutRevalidatingMutatedSource() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    AudienceSnapshotRepository.Snapshot snapshot = snapshot(300L, "LIST", 22L, "5", null);
    when(snapshots.get(7L, 300L)).thenReturn(snapshot);
    when(snapshots.candidateCount(7L, 300L)).thenReturn(42L);

    BroadcastMessage message = message(BroadcastAudienceSourceType.LIST, 22L);
    message.setFrozenAudienceSnapshotId(300L);

    BroadcastAudienceFreezeService.FrozenAudience frozen =
        service.freeze(message, listExpected("5"));

    assertThat(frozen.candidateCount()).isEqualTo(42L);
    verifyNoInteractions(sources);
    verify(snapshots, never()).createForBroadcast(anyLong(), any(), any(), any());
  }

  @Test
  void missingCanonicalConfirmationDescriptorFailsClosed() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);

    assertThatThrownBy(
            () -> service.freeze(message(BroadcastAudienceSourceType.LIST, 22L), null))
        .isInstanceOf(ConflictException.class)
        .hasMessage(BroadcastAudienceFreezeService.STALE_PREVIEW);
    verifyNoInteractions(sources, snapshots);
  }

  @Test
  void legacyBroadcastGroupRemainsCompatibleWithoutDescriptor() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    AudienceSourceResolver sources = mock(AudienceSourceResolver.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots, sources);
    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(7L);
    message.setGroupId(90L);
    message.setChannelType(ChannelType.EMAIL);

    assertThat(service.freeze(message, null)).isNull();
    verifyNoInteractions(sources, snapshots);
  }

  private BroadcastExpectedSourceDescriptor listExpected(String version) {
    return new BroadcastExpectedSourceDescriptor(
        BroadcastAudienceSourceType.LIST,
        "22",
        version,
        null,
        null,
        null,
        Instant.now());
  }

  private BroadcastMessage message(BroadcastAudienceSourceType type, long sourceId) {
    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(7L);
    message.setAudienceSourceType(type);
    message.setAudienceSourceId(sourceId);
    message.setChannelType(ChannelType.EMAIL);
    return message;
  }

  private AudienceSnapshotRepository.Snapshot snapshot(
      long id, String sourceType, long sourceId, String sourceVersion, String filterHash) {
    return new AudienceSnapshotRepository.Snapshot(
        id,
        7L,
        sourceType,
        sourceId,
        sourceVersion,
        filterHash,
        Channel.EMAIL,
        Purpose.MARKETING,
        "PREPARING",
        0,
        0,
        0,
        null,
        Instant.now(),
        null);
  }
}
