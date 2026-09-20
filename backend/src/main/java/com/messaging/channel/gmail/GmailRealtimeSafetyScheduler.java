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
public class GmailRealtimeSafetyScheduler {

  private final GmailOAuthProperties properties;
  private final ChannelConnectionRepository connectionRepository;
  private final GmailTokenService tokenService;
  private final GmailApiClient apiClient;
  private final GmailDistributedQuotaManager quotaManager;
  private final GmailRealtimeStateService stateService;

  @Scheduled(
      fixedDelayString = "${app.channel.gmail.realtime-safety-sync-interval:5m}",
      initialDelayString = "${app.channel.gmail.realtime-safety-sync-initial-delay:2m}")
  public void reconcileCursors() {
    if (!properties.realtimeConfigured()) return;

    var connections =
        connectionRepository.findByProviderTypeAndStatus(
            ProviderType.GMAIL, ChannelConnectionStatus.ACTIVE);

    for (var connection : connections) {
      try {
        stateService.ensureState(connection);

        GmailQuotaReservation reservation =
            quotaManager.reserve(
                connection.getExternalAccountId(),
                GmailQuotaOperation.GET_PROFILE,
                1,
                GmailQuotaTrafficClass.BACKGROUND);
        if (!reservation.allowed()) continue;

        var profile = apiClient.profile(tokenService.accessToken(connection));
        if (profile.historyId() != null && !profile.historyId().isBlank()) {
          stateService.enqueueNotification(connection.getId(), profile.historyId());
        }
      } catch (RuntimeException exception) {
        log.debug(
            "gmail_realtime_safety_check_skipped connectionId={} reason={}",
            connection.getId(),
            exception.getClass().getSimpleName());
      }
    }
  }
}
