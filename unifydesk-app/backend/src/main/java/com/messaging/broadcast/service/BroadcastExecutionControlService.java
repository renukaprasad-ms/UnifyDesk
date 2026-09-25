package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastMessageResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.common.exception.NotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastExecutionControlService {

  private final BroadcastMessageRepository messageRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastStateMachine stateMachine;
  private final BroadcastMessageService messageService;
  private final BroadcastProgressPublisher progressPublisher;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public BroadcastMessageResponse pause(Long companyId, Long broadcastId) {
    BroadcastRun run = requireRun(companyId, broadcastId);
    stateMachine.requirePausable(run.getStatus());
    run.setStatus(BroadcastRunStatus.PAUSED);
    runRepository.save(run);
    BroadcastMessage message = messageRepository.findById(broadcastId).orElseThrow();
    message.setStatus(BroadcastMessageStatus.PAUSED);
    messageRepository.save(message);
    afterCommit(() -> progressPublisher.publish(run.getId(), true));
    return messageService.get(companyId, broadcastId);
  }

  @Transactional
  public BroadcastMessageResponse resume(Long companyId, Long broadcastId) {
    BroadcastRun run = requireRun(companyId, broadcastId);
    stateMachine.requireResumable(run.getStatus());
    run.setStatus(BroadcastRunStatus.RUNNING);
    runRepository.save(run);
    BroadcastMessage message = messageRepository.findById(broadcastId).orElseThrow();
    message.setStatus(BroadcastMessageStatus.RUNNING);
    messageRepository.save(message);
    afterCommit(() -> progressPublisher.publish(run.getId(), true));
    return messageService.get(companyId, broadcastId);
  }

  @Transactional
  public BroadcastMessageResponse cancel(Long companyId, Long broadcastId) {
    BroadcastMessage existingMessage =
        messageRepository
            .findByIdAndCompanyId(broadcastId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));
    BroadcastRun existingRun = runRepository.findByBroadcastMessageId(broadcastId).orElse(null);
    if (existingRun == null && existingMessage.getStatus() == BroadcastMessageStatus.SCHEDULED) {
      existingMessage.setStatus(BroadcastMessageStatus.CANCELLED);
      existingMessage.setScheduledAt(null);
      messageRepository.save(existingMessage);
      return messageService.get(companyId, broadcastId);
    }

    BroadcastRun run = requireRun(companyId, broadcastId);
    stateMachine.requireCancellable(run.getStatus());
    run.setStatus(BroadcastRunStatus.CANCELLED);
    run.setCompletedAt(Instant.now());
    runRepository.save(run);

    BroadcastMessage message = messageRepository.findById(broadcastId).orElseThrow();
    message.setStatus(BroadcastMessageStatus.CANCELLED);
    messageRepository.save(message);

    Long skipped =
        jdbcTemplate.queryForObject(
            """
            with changed as (
              update broadcast_recipients
              set status = 'SKIPPED',
                  failure_code = 'CANCELLED',
                  failure_reason = 'Broadcast cancelled before delivery',
                  claimed_at = null,
                  next_attempt_at = null,
                  updated_at = ?
              where run_id = ? and status in ('PENDING', 'RETRY')
              returning 1
            )
            select count(*) from changed
            """,
            Long.class,
            Timestamp.from(Instant.now()),
            run.getId());
    long count = skipped == null ? 0 : skipped;
    if (count > 0) {
      statsService.transitionCount(
          run.getId(), BroadcastRecipientStatus.PENDING, BroadcastRecipientStatus.SKIPPED, count);
    }

    jdbcTemplate.update(
        """
        update broadcast_batches
        set status = 'CANCELLED', claimed_at = null, next_attempt_at = null, updated_at = ?
        where run_id = ? and status in ('PENDING', 'RETRY')
        """,
        Timestamp.from(Instant.now()),
        run.getId());

    afterCommit(() -> progressPublisher.publish(run.getId(), true));
    return messageService.get(companyId, broadcastId);
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

  private BroadcastRun requireRun(Long companyId, Long broadcastId) {
    BroadcastMessage message =
        messageRepository
            .findByIdAndCompanyId(broadcastId, companyId)
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));
    return runRepository
        .findByBroadcastMessageId(message.getId())
        .orElseThrow(() -> new NotFoundException("Broadcast run not found"));
  }
}
