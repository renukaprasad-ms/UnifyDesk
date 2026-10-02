package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.notification.platform.core.event.NotificationEventRecorder;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastPreparationChunkService {

  private final BroadcastRunRepository runRepository;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastGroupMemberRepository memberRepository;
  private final BroadcastBatchRepository batchRepository;
  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastSuppressionRepository suppressionRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastOutboxService outboxService;
  private final BroadcastProperties properties;
  private final BroadcastProgressPublisher progressPublisher;
  private final BroadcastRunFailureService runFailureService;
  private final AudienceSnapshotService audienceSnapshotService;
  private final NotificationEventRecorder notificationEventRecorder;

  @Transactional
  public void process(Long runId, Long expectedAfterMemberId) {
    BroadcastRun run =
        runRepository
            .findByIdForUpdate(runId)
            .orElseThrow(() -> new NotFoundException("Broadcast run not found"));
    if (run.getStatus() != BroadcastRunStatus.PREPARING) return;

    long currentCursor =
        run.getSnapshotCursorMemberId() == null ? 0L : run.getSnapshotCursorMemberId();
    long expected = expectedAfterMemberId == null ? 0L : expectedAfterMemberId;
    if (currentCursor != expected) return;

    int size = Math.max(1, properties.getSnapshotBatchSize());
    if (run.getAudienceSnapshotId() != null) {
      processAudience(run, currentCursor, size);
      return;
    }
    processLegacyGroup(run, currentCursor, size);
  }

  private void processLegacyGroup(BroadcastRun run, long currentCursor, int size) {
    List<BroadcastGroupMember> members =
        memberRepository.findSnapshotPage(
            run.getGroupId(),
            currentCursor,
            run.getSnapshotMaxMemberId(),
            run.getCreatedAt(),
            PageRequest.of(0, size));

    if (members.isEmpty()) {
      completePreparation(run);
      return;
    }

    Set<String> suppressed =
        activeLegacySuppressions(
            run,
            members.stream()
                .map(BroadcastGroupMember::getNormalizedDestination)
                .collect(java.util.stream.Collectors.toSet()));

    BroadcastBatch batch = createBatch(run, members.size());
    List<BroadcastRecipient> recipients = new ArrayList<>(members.size());
    long pending = 0;
    long skipped = 0;

    for (BroadcastGroupMember member : members) {
      BroadcastRecipient recipient = baseRecipient(run, batch);
      recipient.setSourceMemberId(member.getId());
      recipient.setDestination(member.getDestination());
      recipient.setNormalizedDestination(member.getNormalizedDestination());
      recipient.setDisplayName(member.getDisplayName());
      recipient.setPersonalizationJson(member.getMetadataJson());
      configureTransportMetadata(run, recipient);
      if (suppressed.contains(member.getNormalizedDestination())) {
        markSuppressed(run, recipient);
        skipped++;
      } else {
        recipient.setStatus(BroadcastRecipientStatus.PENDING);
        pending++;
      }
      recipients.add(recipient);
    }

    finishChunk(
        run,
        batch,
        recipients,
        members.getLast().getId(),
        members.size(),
        pending,
        skipped);
  }

  private void processAudience(BroadcastRun run, long currentCursor, int size) {
    AudienceSnapshotRepository.Snapshot snapshot =
        audienceSnapshotService.get(run.getCompanyId(), run.getAudienceSnapshotId());

    if ("PREPARING".equals(snapshot.status())) {
      try {
        audienceSnapshotService.processBatch(run.getCompanyId(), snapshot.id());
      } catch (IllegalStateException exception) {
        if ("Audience source changed after snapshot freeze".equals(exception.getMessage())) {
          audienceSnapshotService.fail(run.getCompanyId(), snapshot.id());
          runFailureService.fail(
              run.getId(),
              "AUDIENCE_SOURCE_CHANGED",
              "The List or Segment changed while the audience snapshot was being materialized");
          return;
        }
        throw exception;
      }
      snapshot = audienceSnapshotService.get(run.getCompanyId(), snapshot.id());
      if ("PREPARING".equals(snapshot.status())) {
        outboxService.enqueueRunPrepare(run.getCompanyId(), run.getId(), currentCursor);
        return;
      }
    }
    if (!"READY".equals(snapshot.status())) {
      throw new IllegalStateException("Audience snapshot is not ready");
    }

    List<AudienceSnapshotRepository.MemberView> members =
        audienceSnapshotService.pageMembers(
            run.getCompanyId(), snapshot.id(), currentCursor, size);
    if (members.isEmpty()) {
      completePreparation(run);
      return;
    }

    Set<String> suppressed =
        activeLegacySuppressions(
            run,
            members.stream()
                .map(AudienceSnapshotRepository.MemberView::normalizedDestination)
                .collect(java.util.stream.Collectors.toSet()));

    BroadcastBatch batch = createBatch(run, members.size());
    List<BroadcastRecipient> recipients = new ArrayList<>(members.size());
    long pending = 0;
    long skipped = 0;

    for (AudienceSnapshotRepository.MemberView member : members) {
      BroadcastRecipient recipient = baseRecipient(run, batch);
      recipient.setSourceMemberId(null);
      recipient.setContactId(member.contactId());
      recipient.setContactIdentityId(member.identityId());
      recipient.setAudienceSnapshotId(snapshot.id());
      recipient.setAudienceSnapshotSequenceId(member.sequenceId());
      recipient.setDestination(member.normalizedDestination());
      recipient.setNormalizedDestination(member.normalizedDestination());
      recipient.setDisplayName(member.displayName());
      configureTransportMetadata(run, recipient);
      if (member.contactId() == null || member.identityId() == null) {
        recipient.setStatus(BroadcastRecipientStatus.SKIPPED);
        recipient.setFailureCode("CONTACT_IDENTITY_MISSING");
        recipient.setFailureReason("Contact or identity was removed after audience snapshot");
        skipped++;
      } else if (suppressed.contains(member.normalizedDestination())) {
        markSuppressed(run, recipient);
        skipped++;
      } else {
        recipient.setStatus(BroadcastRecipientStatus.PENDING);
        pending++;
      }
      recipients.add(recipient);
    }

    finishChunk(
        run,
        batch,
        recipients,
        members.getLast().sequenceId(),
        members.size(),
        pending,
        skipped);
  }

  private BroadcastBatch createBatch(BroadcastRun run, int recipientCount) {
    BroadcastBatch batch = new BroadcastBatch();
    batch.setRunId(run.getId());
    batch.setCompanyId(run.getCompanyId());
    batch.setSequenceNo(run.getBatchCount() + 1);
    batch.setRecipientCount(recipientCount);
    return batchRepository.save(batch);
  }

  private BroadcastRecipient baseRecipient(BroadcastRun run, BroadcastBatch batch) {
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(IdGenerator.nextId());
    recipient.setRunId(run.getId());
    recipient.setBatchId(batch.getId());
    recipient.setCompanyId(run.getCompanyId());
    recipient.setSenderId(run.getSenderId());
    return recipient;
  }

  private void configureTransportMetadata(BroadcastRun run, BroadcastRecipient recipient) {
    String correlationId = UUID.randomUUID().toString();
    recipient.setProviderCorrelationId(correlationId);
    if (run.getProviderType().channelType()
        == com.messaging.channel.enums.ChannelType.EMAIL) {
      recipient.setInternetMessageId(
          "<broadcast." + correlationId + "@" + properties.getMessageIdDomain() + ">");
    }
  }

  private Set<String> activeLegacySuppressions(BroadcastRun run, Set<String> destinations) {
    if (destinations.isEmpty()) return Set.of();
    return new HashSet<>(
        suppressionRepository.findActiveDestinations(
            run.getCompanyId(), run.getProviderType().channelType(), destinations));
  }

  private void markSuppressed(BroadcastRun run, BroadcastRecipient recipient) {
    recipient.setStatus(BroadcastRecipientStatus.SKIPPED);
    recipient.setFailureCode("SUPPRESSED");
    recipient.setFailureReason(
        "Recipient is suppressed for "
            + run.getProviderType().channelType()
            + " broadcasts");
  }

  private void finishChunk(
      BroadcastRun run,
      BroadcastBatch batch,
      List<BroadcastRecipient> recipients,
      long newCursor,
      long total,
      long pending,
      long skipped) {
    recipientRepository.saveAll(recipients);

    batch.setProcessedCount(Math.toIntExact(skipped));
    if (pending == 0) batch.setStatus(BroadcastBatchStatus.COMPLETED);
    batchRepository.save(batch);

    run.setSnapshotCursorMemberId(newCursor);
    run.setTotalRecipients(run.getTotalRecipients() + total);
    run.setBatchCount(run.getBatchCount() + 1);
    runRepository.save(run);
    statsService.addSnapshot(run.getId(), total, pending, skipped);

    outboxService.enqueueRunPrepare(run.getCompanyId(), run.getId(), newCursor);
  }

  private void completePreparation(BroadcastRun run) {
    run.setSnapshotComplete(true);
    BroadcastStatsResponse stats = statsService.get(run.getId());
    BroadcastMessage message =
        messageRepository
            .findById(run.getBroadcastMessageId())
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));
    Instant now = Instant.now();

    if (stats.pending() == 0) {
      run.setStatus(BroadcastRunStatus.COMPLETED);
      run.setStartedAt(now);
      run.setCompletedAt(now);
      message.setStatus(BroadcastMessageStatus.COMPLETED);
      notificationEventRecorder.recordBroadcastCompleted(
          run.getId(),
          message.getId(),
          run.getCompanyId(),
          message.getChannelType().name(),
          run.getCompletedAt());
    } else {
      run.setStatus(BroadcastRunStatus.RUNNING);
      run.setStartedAt(now);
      message.setStatus(BroadcastMessageStatus.RUNNING);
    }
    runRepository.save(run);
    messageRepository.save(message);
    afterCommit(() -> progressPublisher.publish(run.getId(), true));
  }

  private void afterCommit(Runnable action) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isSynchronizationActive()) {
      action.run();
      return;
    }
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                action.run();
              }
            });
  }
}
