package com.messaging.channel.microsoft;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient.*;
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftSendService {

  private final ChannelConnectionRepository connectionRepository;
  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftSyncStateService syncStateService;

  public MicrosoftSendResponse send(
      Long companyId, Long connectionId, MicrosoftSendRequest request) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Microsoft connection not found"));

    if (connection.getProviderType() != ProviderType.MICROSOFT) {
      throw new BadRequestException("Channel connection is not a Microsoft mailbox");
    }
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Microsoft mailbox is not active");
    }

    List<String> to = normalize(request.to());
    List<String> cc = normalize(request.cc());
    List<String> bcc = normalize(request.bcc());
    if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    GraphSendMessage message =
        new GraphSendMessage(
            trimToEmpty(request.subject()),
            new GraphBody("Text", request.bodyText()),
            recipients(to),
            recipients(cc),
            recipients(bcc));

    apiClient.sendMail(
        tokenService.accessToken(connection), new SendMailRequest(message, true));

    // Graph sendMail is asynchronous (202). Delta sync will reconcile the provider-assigned
    // message ID and conversation ID from Sent Items without fabricating local identifiers.
    syncStateService.triggerConnection(connectionId);

    return new MicrosoftSendResponse(
        connectionId.toString(),
        connection.getExternalAccountId(),
        to,
        cc,
        bcc,
        trimToEmpty(request.subject()),
        Instant.now());
  }

  private List<GraphRecipient> recipients(List<String> values) {
    return values.stream()
        .map(value -> new GraphRecipient(new GraphEmailAddress(null, value)))
        .toList();
  }

  private List<String> normalize(List<String> values) {
    if (values == null) return List.of();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        unique.add(value.trim().toLowerCase(Locale.ROOT));
      }
    }
    return List.copyOf(unique);
  }

  private String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
