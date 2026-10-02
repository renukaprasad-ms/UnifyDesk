package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastAudienceFreezeService {

  private final AudienceSnapshotService snapshotService;

  /**
   * Freezes membership only. Eligibility remains bounded/asynchronous in Broadcast preparation.
   */
  public FrozenAudience freeze(BroadcastMessage message) {
    if (message.getGroupId() != null) return null;
    AudienceSnapshotRepository.Snapshot snapshot;
    if (message.getFrozenAudienceSnapshotId() != null) {
      snapshot =
          snapshotService.get(
              message.getCompanyId(), message.getFrozenAudienceSnapshotId());
    } else {
      AudienceSourceResolver.SourceType sourceType =
          AudienceSourceResolver.SourceType.valueOf(message.getAudienceSourceType().name());
      snapshot =
          snapshotService.createForBroadcast(
              message.getCompanyId(),
              sourceType,
              message.getAudienceSourceId(),
              Channel.valueOf(message.getChannelType().name()),
              Purpose.MARKETING);
    }
    return new FrozenAudience(
        snapshot, snapshotService.candidateCount(message.getCompanyId(), snapshot.id()));
  }

  public record FrozenAudience(
      AudienceSnapshotRepository.Snapshot snapshot, long candidateCount) {}
}
