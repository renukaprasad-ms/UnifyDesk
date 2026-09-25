package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastRunStatus;
import com.messaging.broadcast.repository.BroadcastRunRepository;
import com.messaging.realtime.service.RealtimeService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastProgressPublisher {

  public static final String EVENT_TYPE = "broadcast.progress.changed";

  private final BroadcastRunRepository runRepository;
  private final BroadcastStatsService statsService;
  private final RealtimeService realtimeService;
  private final StringRedisTemplate redisTemplate;

  public void publish(Long runId, boolean force) {
    String throttleKey = "messaging:broadcast:progress:" + runId;
    if (!force
        && !Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(throttleKey, "1", Duration.ofSeconds(1)))) {
      return;
    }

    BroadcastRun run = runRepository.findById(runId).orElse(null);
    if (run == null) return;
    BroadcastStatsResponse stats = statsService.get(runId);
    long completed =
        stats.accepted() + stats.failed() + stats.bounced() + stats.unknown() + stats.skipped();
    int progress =
        stats.total() <= 0
            ? 0
            : (int) Math.min(isTerminal(run.getStatus()) ? 100 : 99, completed * 100 / stats.total());

    realtimeService.sendToCompany(
        run.getCompanyId(),
        EVENT_TYPE,
        new BroadcastProgressEvent(
            run.getId().toString(),
            run.getBroadcastMessageId().toString(),
            run.getGroupId().toString(),
            run.getStatus(),
            stats,
            progress));

    if (force && isTerminal(run.getStatus())) redisTemplate.delete(throttleKey);
  }

  private boolean isTerminal(BroadcastRunStatus status) {
    return status == BroadcastRunStatus.COMPLETED
        || status == BroadcastRunStatus.PARTIALLY_FAILED
        || status == BroadcastRunStatus.CANCELLED
        || status == BroadcastRunStatus.FAILED;
  }
}
