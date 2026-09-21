package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.repository.BroadcastMessageRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastScheduleService {

  private final BroadcastMessageRepository messageRepository;

  @Transactional
  public List<DueBroadcast> claimDue(int limit) {
    List<BroadcastMessage> due =
        messageRepository.findDueForUpdate(Instant.now(), Math.max(1, limit));
    for (BroadcastMessage message : due) {
      message.setStatus(BroadcastMessageStatus.QUEUED);
    }
    messageRepository.saveAll(due);
    return due.stream()
        .map(message -> new DueBroadcast(message.getCompanyId(), message.getId()))
        .toList();
  }

  @Transactional
  public void fail(Long companyId, Long broadcastId) {
    messageRepository
        .findByIdAndCompanyId(broadcastId, companyId)
        .ifPresent(
            message -> {
              if (message.getStatus() == BroadcastMessageStatus.QUEUED) {
                message.setStatus(BroadcastMessageStatus.FAILED);
                messageRepository.save(message);
              }
            });
  }

  public record DueBroadcast(Long companyId, Long broadcastId) {}
}
