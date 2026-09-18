package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.MessageListResponse;
import com.messaging.channel.gmail.GmailApiClient.MessageReference;
import com.messaging.channel.gmail.dto.GmailSyncResult;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncService {

  private final GmailOAuthProperties properties;
  private final GmailApiClient gmailApiClient;
  private final GmailTokenService tokenService;
  private final GmailInboxPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;

  public GmailSyncResult sync(Long companyId, Long connectionId) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

    if (connection.getProviderType() != ProviderType.GMAIL) {
      throw new BadRequestException("Channel connection is not a Gmail account");
    }
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Gmail connection is not active");
    }

    String accessToken = tokenService.accessToken(connection);
    int maxMessages = Math.min(Math.max(properties.getInitialSyncMaxMessages(), 1), 100);
    int days = Math.max(properties.getInitialSyncDays(), 1);
    String query = "newer_than:" + days + "d";

    MessageListResponse listResponse =
        gmailApiClient.listMessages(accessToken, maxMessages, query);
    List<MessageReference> references =
        listResponse.messages() == null ? List.of() : listResponse.messages();

    List<GmailMessage> messages = new ArrayList<>(references.size());
    for (MessageReference reference : references) {
      if (reference == null || reference.id() == null || reference.id().isBlank()) {
        continue;
      }
      messages.add(gmailApiClient.message(accessToken, reference.id()));
    }

    GmailProfile profile = gmailApiClient.profile(accessToken);
    return persistenceService.persistFullSync(connection, messages, profile.historyId());
  }
}
