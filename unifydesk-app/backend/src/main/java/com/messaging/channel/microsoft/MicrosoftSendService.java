package com.messaging.channel.microsoft;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.microsoft.MicrosoftApiClient.*;
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftSendService {

  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftSyncStateService syncStateService;

  public MicrosoftSendResponse send(
      ChannelConnection connection, MicrosoftSendRequest request) {
    Long connectionId = connection.getId();

    List<String> to = normalize(request.to());
    List<String> cc = normalize(request.cc());
    List<String> bcc = normalize(request.bcc());
    boolean reply = request.replyToMessageId() != null && !request.replyToMessageId().isBlank();
    if (!reply && to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    String accessToken = tokenService.accessToken(connection);
    if (reply) {
      apiClient.reply(
          accessToken,
          request.replyToMessageId().trim(),
          request.bodyText(),
          Boolean.TRUE.equals(request.replyAll()));
    } else {
      GraphSendMessage message =
          new GraphSendMessage(
              trimToEmpty(request.subject()),
              new GraphBody("Text", request.bodyText()),
              recipients(to),
              recipients(cc),
              recipients(bcc));
      apiClient.sendMail(accessToken, new SendMailRequest(message, true));
    }

    // Graph send/reply is asynchronous (202). Delta sync reconciles provider-assigned IDs
    // from Sent Items without fabricating local identifiers.
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
