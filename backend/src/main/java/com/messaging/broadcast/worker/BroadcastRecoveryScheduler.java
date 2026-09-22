package com.messaging.broadcast.worker;

import com.messaging.broadcast.service.BroadcastRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BroadcastRecoveryScheduler {

  private final BroadcastRecoveryService recoveryService;

  @Scheduled(fixedDelayString = "${app.broadcast.recovery-interval-ms:60000}")
  public void recover() {
    recoveryService.recover();
  }
}
