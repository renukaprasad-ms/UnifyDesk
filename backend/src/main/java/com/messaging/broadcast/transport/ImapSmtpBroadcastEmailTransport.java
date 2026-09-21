package com.messaging.broadcast.transport;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.imapsmtp.*;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendRequest;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImapSmtpBroadcastEmailTransport implements BroadcastEmailTransport {

  private final ImapSmtpConnectionService connectionService;
  private final ImapSmtpMailClient mailClient;

  @Override
  public ProviderType providerType() {
    return ProviderType.IMAP_SMTP;
  }

  @Override
  public BroadcastEmailResult send(
      ChannelConnection connection, BroadcastEmailCommand command) {
    ImapSmtpStoredCredentials credentials = connectionService.credentials(connection);
    try {
      ImapSmtpMailClient.SentMessage sent =
          mailClient.send(
              credentials,
              new ImapSmtpSendRequest(
                  List.of(command.recipient()),
                  List.of(),
                  List.of(),
                  command.subject(),
                  command.bodyText(),
                  null,
                  null),
              command.requestedInternetMessageId(),
              command.correlationId());
      return new BroadcastEmailResult(
          sent.messageId(), sent.messageId(), sent.sentAt());
    } catch (BadRequestException exception) {
      throw BroadcastTransportException.permanent(
          "SMTP_REJECTED", exception.getMessage(), exception);
    } catch (ServiceUnavailableException exception) {
      // SMTP failures after DATA can be ambiguous. Avoid blind retries that may duplicate mail.
      throw BroadcastTransportException.unknown(
          "SMTP_SEND_UNKNOWN", "SMTP send outcome could not be confirmed", exception);
    }
  }
}
