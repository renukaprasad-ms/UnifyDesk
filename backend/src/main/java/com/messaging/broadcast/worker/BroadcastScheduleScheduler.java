package com.messaging.broadcast.worker;

import com.messaging.broadcast.service.BroadcastScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BroadcastScheduleScheduler {

  private final BroadcastScheduleService scheduleService;

  @Scheduled(fixedDelayString = "${app.broadcast.schedule-poll-interval-ms:1000}")
  public void launchDue() {
    scheduleService.launchDue(20);
  }
}
