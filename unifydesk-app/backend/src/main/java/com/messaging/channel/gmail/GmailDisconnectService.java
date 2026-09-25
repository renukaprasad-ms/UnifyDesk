package com.messaging.channel.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.CredentialType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailDisconnectService {

  private final GmailWatchService watchService;
  private final ProviderCredentialService credentialService;
  private final GmailApiClient gmailApiClient;
  private final ObjectMapper objectMapper;

  public void disconnect(ChannelConnection connection) {
    watchService.stopBestEffort(connection);
    revokeBestEffort(connection);
  }

  private void revokeBestEffort(ChannelConnection connection) {
    try {
      String raw = credentialService.read(connection.getId(), CredentialType.OAUTH_TOKEN_SET);
      GmailStoredToken stored = objectMapper.readValue(raw, GmailStoredToken.class);
      String token = hasText(stored.refreshToken()) ? stored.refreshToken() : stored.accessToken();
      gmailApiClient.revokeBestEffort(token);
    } catch (Exception exception) {
      log.warn(
          "Unable to revoke Gmail token during disconnect connectionId={} reason={}",
          connection.getId(),
          exception.getClass().getSimpleName());
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
