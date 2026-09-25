package com.messaging.broadcast.worker;

import com.messaging.broadcast.service.BroadcastLaunchService;
import com.messaging.broadcast.service.BroadcastScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastScheduleScheduler {

  private final BroadcastScheduleService scheduleService;
  private final BroadcastLaunchService launchService;

  @Scheduled(fixedDelayString = "${app.broadcast.schedule-poll-interval-ms:1000}")
  public void launchDue() {
    for (BroadcastScheduleService.DueBroadcast due : scheduleService.claimDue(20)) {
      try {
        launchService.launchClaimed(due.companyId(), due.broadcastId());
      } catch (RuntimeException exception) {
        log.error(
            "scheduled_broadcast_launch_failed companyId={} broadcastId={} reason={}",
            due.companyId(),
            due.broadcastId(),
            exception.getClass().getSimpleName(),
            exception);
        scheduleService.fail(due.companyId(), due.broadcastId());
      }
    }
  }
}
