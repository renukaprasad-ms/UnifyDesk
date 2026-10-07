package com.messaging.channel.zoho;

import com.messaging.channel.entity.ChannelConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoMailDisconnectService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;

  public void disconnect(ChannelConnection connection) {
    try {
      ZohoMailStoredToken token = tokenService.stored(connection);
      apiClient.revokeRefreshToken(token.accountsServer(), token.refreshToken());
    } catch (RuntimeException exception) {
      log.warn(
          "zoho_mail_token_revoke_failed connectionId={} reason={}",
          connection.getId(),
          exception.getClass().getSimpleName());
    }
  }
}
