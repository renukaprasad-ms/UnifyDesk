package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastMessage;
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
  private final BroadcastLaunchService launchService;

  @Transactional
  public int launchDue(int limit) {
    List<BroadcastMessage> due =
        messageRepository.findDueForUpdate(Instant.now(), Math.max(1, limit));
    for (BroadcastMessage message : due) {
      launchService.launchScheduled(message);
    }
    return due.size();
  }
}
