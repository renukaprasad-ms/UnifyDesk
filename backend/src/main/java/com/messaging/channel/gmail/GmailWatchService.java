package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.GmailApiClient.WatchResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailWatchService {

  private final GmailOAuthProperties properties;
  private final GmailTokenService tokenService;
  private final GmailApiClient apiClient;
  private final GmailDistributedQuotaManager quotaManager;
  private final GmailRealtimeStateService stateService;

  public boolean ensureWatch(ChannelConnection connection, boolean force) {
    if (!properties.realtimeConfigured()) {
      return false;
    }

    var state = stateService.state(connection.getId());
    if (!force
        && state != null
        && state.getWatchRenewedAt() != null
        && state.getWatchRenewedAt().isAfter(Instant.now().minus(properties.getWatchRenewAfter()))
        && state.getWatchExpirationAt() != null
        && state.getWatchExpirationAt().isAfter(Instant.now().plusSeconds(3600))) {
      return true;
    }

    GmailQuotaReservation reservation =
        quotaManager.reserve(
            connection.getExternalAccountId(),
            GmailQuotaOperation.WATCH,
            1,
            GmailQuotaTrafficClass.BACKGROUND);
    if (!reservation.allowed()) {
      return false;
    }

    String accessToken = tokenService.accessToken(connection);
    WatchResponse response = apiClient.watch(accessToken, properties.getPubsubTopicName());
    stateService.recordWatch(connection, response.historyId(), response.expiration());

    log.info(
        "gmail_watch_active connectionId={} account={} expiration={}",
        connection.getId(),
        connection.getExternalAccountId(),
        response.expiration());
    return true;
  }

  public void stopBestEffort(ChannelConnection connection) {
    if (!properties.realtimeConfigured()) return;

    try {
      GmailQuotaReservation reservation =
          quotaManager.reserve(
              connection.getExternalAccountId(),
              GmailQuotaOperation.STOP_WATCH,
              1,
              GmailQuotaTrafficClass.INTERACTIVE);
      if (!reservation.allowed()) return;

      apiClient.stopWatch(tokenService.accessToken(connection));
    } catch (RuntimeException exception) {
      log.warn(
          "Gmail watch stop failed connectionId={} reason={}",
          connection.getId(),
          exception.getClass().getSimpleName());
    }
  }
}
