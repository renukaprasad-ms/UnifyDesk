package com.messaging.channel.gmail;

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
public class GmailWatchRenewalScheduler {

  private final GmailOAuthProperties properties;
  private final ChannelConnectionRepository connectionRepository;
  private final GmailWatchService watchService;

  @Scheduled(
      fixedDelayString = "${app.channel.gmail.watch-renew-interval:1h}",
      initialDelayString = "${app.channel.gmail.watch-renew-initial-delay:1m}")
  public void renew() {
    if (!properties.realtimeConfigured()) return;

    var connections =
        connectionRepository.findByProviderTypeAndStatus(
            ProviderType.GMAIL, ChannelConnectionStatus.ACTIVE);

    int renewed = 0;
    for (var connection : connections) {
      try {
        if (watchService.ensureWatch(connection, false)) {
          renewed++;
        }
      } catch (RuntimeException exception) {
        log.warn(
            "gmail_watch_renew_failed connectionId={} account={} reason={}",
            connection.getId(),
            connection.getExternalAccountId(),
            exception.getClass().getSimpleName());
      }
    }

    if (!connections.isEmpty()) {
      log.debug(
          "gmail_watch_renewal_checked active={} ready={}",
          connections.size(),
          renewed);
    }
  }
}
