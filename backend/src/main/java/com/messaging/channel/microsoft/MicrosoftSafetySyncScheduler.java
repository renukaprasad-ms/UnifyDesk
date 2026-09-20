package com.messaging.channel.microsoft;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MicrosoftSafetySyncScheduler {

  private final ChannelConnectionRepository connectionRepository;
  private final MicrosoftSyncStateService syncStateService;

  @Scheduled(
      fixedDelayString = "${app.channel.microsoft.safety-sync-interval:5m}",
      initialDelayString = "${app.channel.microsoft.safety-sync-initial-delay:2m}")
  public void triggerActiveMailboxes() {
    var connections =
        connectionRepository.findByProviderTypeAndStatus(
            ProviderType.MICROSOFT, ChannelConnectionStatus.ACTIVE);

    for (var connection : connections) {
      try {
        syncStateService.triggerConnection(connection.getId());
      } catch (RuntimeException exception) {
        log.warn(
            "Microsoft safety sync trigger failed connectionId={} reason={}",
            connection.getId(),
            exception.getClass().getSimpleName());
      }
    }
  }
}
