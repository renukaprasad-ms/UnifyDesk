package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastExpectedSourceDescriptor;
import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.audience.AudienceSourceResolver.BroadcastSourceState;
import com.messaging.contacts.audience.AudienceSourceResolver.SourceChangedException;
import com.messaging.contacts.audience.AudienceSourceResolver.SourceType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastAudienceFreezeService {

  public static final String STALE_PREVIEW = "BROADCAST_AUDIENCE_PREVIEW_STALE";

  private final AudienceSnapshotService snapshotService;
  private final AudienceSourceResolver sourceResolver;

  /**
   * Freezes membership only. Eligibility remains bounded/asynchronous in Broadcast preparation. The
   * first canonical freeze must be bound to the exact source descriptor confirmed by the customer.
   * Once frozen, retries reuse the immutable snapshot without consulting mutable source state
   * again.
   */
  public FrozenAudience freeze(
      BroadcastMessage message, BroadcastExpectedSourceDescriptor expectedDescriptor) {
    if (message.getGroupId() != null) return null;

    AudienceSnapshotRepository.Snapshot snapshot;
    if (message.getFrozenAudienceSnapshotId() != null) {
      snapshot = snapshotService.get(message.getCompanyId(), message.getFrozenAudienceSnapshotId());
    } else {
      BroadcastSourceState current = validateExpectedDescriptor(message, expectedDescriptor);
      try {
        snapshot =
            snapshotService.createForBroadcast(
                message.getCompanyId(),
                current,
                Channel.valueOf(message.getChannelType().name()),
                Purpose.MARKETING);
      } catch (SourceChangedException | NotFoundException changedDuringCapture) {
        throw stalePreview();
      }
    }
    return new FrozenAudience(
        snapshot, snapshotService.candidateCount(message.getCompanyId(), snapshot.id()));
  }

  public FrozenAudience freeze(BroadcastMessage message) {
    return freeze(message, null);
  }

  private BroadcastSourceState validateExpectedDescriptor(
      BroadcastMessage message, BroadcastExpectedSourceDescriptor expected) {
    if (expected == null
        || expected.sourceType() == null
        || expected.sourceVersion() == null
        || expected.sourceVersion().isBlank()
        || expected.resolvedAt() == null
        || expected.sourceType() != message.getAudienceSourceType()) {
      throw stalePreview();
    }

    SourceType sourceType = SourceType.valueOf(message.getAudienceSourceType().name());
    BroadcastSourceState current;
    try {
      current =
          sourceResolver.describeForBroadcast(
              message.getCompanyId(), sourceType, message.getAudienceSourceId());
    } catch (NotFoundException sourceMissing) {
      throw stalePreview();
    }

    if (sourceType == SourceType.CONTACTS) {
      if (expected.contactSelectionHash() == null
          || expected.contactSelectionHash().isBlank()
          || !expected.contactSelectionHash().equals(expected.sourceVersion())
          || !Objects.equals(current.contactSelectionHash(), expected.contactSelectionHash())
          || !Objects.equals(current.source().sourceVersion(), expected.sourceVersion())) {
        throw stalePreview();
      }
      if (expected.sourceId() != null
          && !Long.toString(message.getAudienceSourceId()).equals(expected.sourceId())) {
        throw stalePreview();
      }
      return current;
    }

    if (expected.sourceId() == null
        || !Long.toString(message.getAudienceSourceId()).equals(expected.sourceId())
        || current.source().type() != sourceType
        || current.source().id() != message.getAudienceSourceId()
        || !Objects.equals(current.source().sourceVersion(), expected.sourceVersion())) {
      throw stalePreview();
    }

    if (sourceType == SourceType.SEGMENT
        && (expected.filterVersion() == null
            || expected.filterHash() == null
            || expected.filterHash().isBlank()
            || !Objects.equals(current.filterVersion(), expected.filterVersion())
            || !Objects.equals(current.source().filterHash(), expected.filterHash()))) {
      throw stalePreview();
    }

    return current;
  }

  private ConflictException stalePreview() {
    return new ConflictException(STALE_PREVIEW);
  }

  public record FrozenAudience(AudienceSnapshotRepository.Snapshot snapshot, long candidateCount) {}
}
