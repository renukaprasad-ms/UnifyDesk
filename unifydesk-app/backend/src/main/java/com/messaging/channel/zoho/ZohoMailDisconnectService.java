package com.messaging.channel.zoho;

import com.messaging.channel.entity.ChannelConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailDisconnectService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;
  private final ZohoMailWebhookService webhookService;

  public void disconnect(ChannelConnection connection) {
    // Reject callbacks for the disconnected mailbox, even if later reconnected.
    webhookService.disconnect(connection.getId());
    try {
      ZohoMailStoredToken token = tokenService.stored(connection);
      apiClient.revokeBestEffort(token.accountsServer(), token.refreshToken());
    } catch (RuntimeException ignored) {
      // Local disconnect must still complete even when the remote token is already invalid.
    }
  }
}
