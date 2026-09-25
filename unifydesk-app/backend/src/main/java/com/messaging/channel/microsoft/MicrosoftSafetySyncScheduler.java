package com.messaging.channel.microsoft;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrosoftSafetySyncScheduler {

  private final MicrosoftSyncStateService syncStateService;

  @Scheduled(
      fixedDelayString = "${app.channel.microsoft.safety-sync-interval:5m}",
      initialDelayString = "${app.channel.microsoft.safety-sync-initial-delay:2m}")
  public void scheduleStaleCursors() {
    syncStateService.scheduleSafetySync(Duration.ofMinutes(5));
  }
}
