package com.messaging.broadcast.transport;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.*;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailBroadcastEmailTransport implements BroadcastEmailTransport {

  private final GmailTokenService tokenService;
  private final GmailApiClient apiClient;
  private final GmailDistributedQuotaManager quotaManager;

  @Override
  public ProviderType providerType() {
    return ProviderType.GMAIL;
  }

  @Override
  public BroadcastEmailResult send(
      ChannelConnection connection, BroadcastEmailCommand command) {
    String account = connection.getExternalAccountId();
    if (account == null || account.isBlank()) {
      throw BroadcastTransportException.permanent(
          "SENDER_MISSING", "Gmail sender address is missing", null);
    }

    GmailQuotaReservation reservation =
        quotaManager.reserve(
            account,
            GmailQuotaOperation.SEND_MESSAGE,
            1,
            GmailQuotaTrafficClass.BACKGROUND);
    if (!reservation.allowed()) {
      throw BroadcastTransportException.retryable(
          "GMAIL_QUOTA", "Gmail broadcast quota is temporarily exhausted", null);
    }

    String accessToken;
    try {
      accessToken = tokenService.accessToken(connection);
    } catch (ServiceUnavailableException exception) {
      throw BroadcastTransportException.retryable(
          "TOKEN_TEMPORARY", exception.getMessage(), exception);
    } catch (BadRequestException exception) {
      throw BroadcastTransportException.permanent(
          "TOKEN_INVALID", exception.getMessage(), exception);
    }

    try {
      GmailApiClient.SendMessageResponse response =
          apiClient.sendMessageOnce(accessToken, raw(account, command), null);
      return new BroadcastEmailResult(
          response.id(), command.requestedInternetMessageId(), Instant.now());
    } catch (GmailRateLimitException exception) {
      quotaManager.penalize(account, 1, exception.retryAfter());
      throw BroadcastTransportException.retryable(
          "GMAIL_RATE_LIMIT", "Gmail rate limited the broadcast", exception);
    } catch (BadRequestException exception) {
      throw BroadcastTransportException.permanent(
          "GMAIL_REJECTED", exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      // Once the network send has started, a timeout can mean the provider accepted the message
      // but the response was lost. Do not auto-retry an ambiguous send.
      throw BroadcastTransportException.unknown(
          "GMAIL_SEND_UNKNOWN", "Gmail send outcome could not be confirmed", exception);
    }
  }

  private String raw(String from, BroadcastEmailCommand command) {
    try {
      Session session = Session.getInstance(new Properties());
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from, true));
      message.setRecipient(
          Message.RecipientType.TO, new InternetAddress(command.recipient(), true));
      message.setSubject(
          command.subject() == null ? "" : command.subject(), StandardCharsets.UTF_8.name());
      message.setText(command.bodyText(), StandardCharsets.UTF_8.name());
      message.setSentDate(new Date());
      message.saveChanges();
      message.setHeader("Message-ID", command.requestedInternetMessageId());
      message.setHeader("X-Messaging-Broadcast-Recipient", command.correlationId());

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      message.writeTo(output);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    } catch (Exception exception) {
      throw BroadcastTransportException.permanent(
          "INVALID_EMAIL", "Unable to construct Gmail broadcast message", exception);
    }
  }
}
