package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
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

    Set<String> destinations =
        members.stream()
            .map(BroadcastGroupMember::getNormalizedDestination)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> suppressed =
        destinations.isEmpty()
            ? Set.of()
            : new HashSet<>(
                suppressionRepository.findActiveDestinations(
                    run.getCompanyId(),
                    run.getProviderType().channelType(),
                    destinations));

    BroadcastBatch batch = new BroadcastBatch();
    batch.setRunId(run.getId());
    batch.setCompanyId(run.getCompanyId());
    batch.setSequenceNo(run.getBatchCount() + 1);
    batch.setRecipientCount(members.size());
    batch = batchRepository.save(batch);

    List<BroadcastRecipient> recipients = new ArrayList<>(members.size());
    long pending = 0;
    long skipped = 0;
    for (BroadcastGroupMember member : members) {
      BroadcastRecipient recipient = new BroadcastRecipient();
      recipient.setId(IdGenerator.nextId());
      recipient.setRunId(run.getId());
      recipient.setBatchId(batch.getId());
      recipient.setCompanyId(run.getCompanyId());
      recipient.setSenderId(run.getSenderId());
      recipient.setSourceMemberId(member.getId());
      recipient.setDestination(member.getDestination());
      recipient.setNormalizedDestination(member.getNormalizedDestination());
      recipient.setDisplayName(member.getDisplayName());
      String correlationId = UUID.randomUUID().toString();
      recipient.setProviderCorrelationId(correlationId);
      if (run.getProviderType().channelType()
          == com.messaging.channel.enums.ChannelType.EMAIL) {
        recipient.setInternetMessageId(
            "<broadcast." + correlationId + "@" + properties.getMessageIdDomain() + ">");
      }
      if (suppressed.contains(member.getNormalizedDestination())) {
        recipient.setStatus(BroadcastRecipientStatus.SKIPPED);
        recipient.setFailureCode("SUPPRESSED");
        recipient.setFailureReason(
            "Recipient is suppressed for " + run.getProviderType().channelType() + " broadcasts");
        skipped++;
      } else {
        recipient.setStatus(BroadcastRecipientStatus.PENDING);
        pending++;
      }
      recipients.add(recipient);
    }
    recipientRepository.saveAll(recipients);

    batch.setProcessedCount(Math.toIntExact(skipped));
    if (pending == 0) batch.setStatus(BroadcastBatchStatus.COMPLETED);
    batchRepository.save(batch);

    long newCursor = members.getLast().getId();
    run.setSnapshotCursorMemberId(newCursor);
    run.setTotalRecipients(run.getTotalRecipients() + members.size());
    run.setBatchCount(run.getBatchCount() + 1);
    runRepository.save(run);
    statsService.addSnapshot(run.getId(), members.size(), pending, skipped);

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
