package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.channel.enums.ChannelType;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BroadcastAudienceFreezeServiceTests {

  @Test
  void createsOneFrozenSnapshotAndReturnsCapturedCandidateCount() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots);
    AudienceSnapshotRepository.Snapshot snapshot = snapshot(300L);

    when(snapshots.createForBroadcast(
            7L, AudienceSourceResolver.SourceType.LIST, 22L, Channel.EMAIL, Purpose.MARKETING))
        .thenReturn(snapshot);
    when(snapshots.candidateCount(7L, 300L)).thenReturn(42L);

    BroadcastMessage message = message();
    BroadcastAudienceFreezeService.FrozenAudience frozen = service.freeze(message);

    assertThat(frozen.snapshot().id()).isEqualTo(300L);
    assertThat(frozen.candidateCount()).isEqualTo(42L);
    verify(snapshots, times(1))
        .createForBroadcast(
            7L, AudienceSourceResolver.SourceType.LIST, 22L, Channel.EMAIL, Purpose.MARKETING);
  }

  @Test
  void existingFrozenSnapshotIsReusedInsteadOfReresolvingSource() {
    AudienceSnapshotService snapshots = mock(AudienceSnapshotService.class);
    BroadcastAudienceFreezeService service = new BroadcastAudienceFreezeService(snapshots);
    AudienceSnapshotRepository.Snapshot snapshot = snapshot(300L);
    when(snapshots.get(7L, 300L)).thenReturn(snapshot);
    when(snapshots.candidateCount(7L, 300L)).thenReturn(42L);

    BroadcastMessage message = message();
    message.setFrozenAudienceSnapshotId(300L);

    BroadcastAudienceFreezeService.FrozenAudience frozen = service.freeze(message);

    assertThat(frozen.candidateCount()).isEqualTo(42L);
    verify(snapshots, never())
        .createForBroadcast(anyLong(), any(), anyLong(), any(), any());
  }

  private BroadcastMessage message() {
    BroadcastMessage message = new BroadcastMessage();
    message.setCompanyId(7L);
    message.setAudienceSourceType(BroadcastAudienceSourceType.LIST);
    message.setAudienceSourceId(22L);
    message.setChannelType(ChannelType.EMAIL);
    return message;
  }

  private AudienceSnapshotRepository.Snapshot snapshot(long id) {
    return new AudienceSnapshotRepository.Snapshot(
        id,
        7L,
        "LIST",
        22L,
        "5",
        null,
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
