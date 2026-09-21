package com.messaging.broadcast.transport;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient;
import com.messaging.channel.microsoft.MicrosoftTokenService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrosoftBroadcastEmailTransport implements BroadcastEmailTransport {

  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;

  @Override
  public ProviderType providerType() {
    return ProviderType.MICROSOFT;
  }

  @Override
  public BroadcastEmailResult send(
      ChannelConnection connection, BroadcastEmailCommand command) {
    String accessToken;
    try {
      accessToken = tokenService.accessToken(connection);
    } catch (ServiceUnavailableException | TooManyRequestsException exception) {
      throw BroadcastTransportException.retryable(
          "TOKEN_TEMPORARY", exception.getMessage(), exception);
    } catch (BadRequestException exception) {
      throw BroadcastTransportException.permanent(
          "TOKEN_INVALID", exception.getMessage(), exception);
    }

    MicrosoftApiClient.GraphRecipient recipient =
        new MicrosoftApiClient.GraphRecipient(
            new MicrosoftApiClient.GraphEmailAddress(null, command.recipient()));
    MicrosoftApiClient.GraphSendMessage message =
        new MicrosoftApiClient.GraphSendMessage(
            command.subject() == null ? "" : command.subject(),
            new MicrosoftApiClient.GraphBody("Text", command.bodyText()),
            List.of(recipient),
            List.of(),
            List.of(),
            List.of(
                new MicrosoftApiClient.GraphHeader(
                    "x-messaging-broadcast-recipient", command.correlationId())));

    try {
      apiClient.sendMail(accessToken, new MicrosoftApiClient.SendMailRequest(message, true));
      return new BroadcastEmailResult(null, null, Instant.now());
    } catch (TooManyRequestsException exception) {
      throw BroadcastTransportException.retryable(
          "MICROSOFT_RATE_LIMIT", exception.getMessage(), exception);
    } catch (BadRequestException exception) {
      throw BroadcastTransportException.permanent(
          "MICROSOFT_REJECTED", exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      throw BroadcastTransportException.unknown(
          "MICROSOFT_SEND_UNKNOWN",
          "Microsoft accepted-state could not be confirmed",
          exception);
    }
  }
}
