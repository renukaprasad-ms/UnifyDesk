package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastTestEmailResponse;
import com.messaging.broadcast.dto.SendBroadcastTestEmailRequest;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailResult;
import com.messaging.broadcast.transport.BroadcastEmailTransportRegistry;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.id.IdGenerator;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastSenderTestService {

  private final BroadcastSenderService senderService;
  private final BroadcastEmailTransportRegistry emailTransportRegistry;

  public BroadcastTestEmailResponse sendTestEmail(
      Long companyId, Long senderId, SendBroadcastTestEmailRequest request) {
    BroadcastSender sender = senderService.requireActive(companyId, senderId);
    if (sender.getProvider().channelType() != ChannelType.EMAIL) {
      throw new BadRequestException("Test email is only available for email Broadcast providers");
    }

    BroadcastSenderConnection connection =
        senderService.requireConnection(companyId, sender.getConnectionId());
    String recipient = normalizeEmail(request.recipient());
    String correlationId = "test-" + IdGenerator.nextId();
    String subject =
        request.subject() == null || request.subject().isBlank()
            ? "Messaging test email"
            : request.subject().trim();
    String body =
        request.bodyText() == null || request.bodyText().isBlank()
            ? "This is a test email from Messaging. Your Broadcast provider connection is working."
            : request.bodyText().trim();

    try {
      BroadcastEmailResult result =
          emailTransportRegistry
              .require(sender.getProvider())
              .send(
                  connection,
                  sender,
                  new BroadcastEmailCommand(
                      recipient, subject, body, null, correlationId));
      return new BroadcastTestEmailResponse(
          sender.getProvider(),
          recipient,
          result.providerMessageId(),
          result.acceptedAt());
    } catch (BroadcastTransportException exception) {
      throw new BadRequestException(
          "Provider test send failed"
              + (exception.code() == null ? "" : " (" + exception.code() + ")")
              + ": "
              + safeMessage(exception));
    }
  }

  private String normalizeEmail(String value) {
    try {
      InternetAddress address = new InternetAddress(value.trim(), true);
      address.validate();
      return address.getAddress().trim().toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      throw new BadRequestException("Test recipient email is invalid");
    }
  }

  private String safeMessage(Throwable throwable) {
    String value = throwable.getMessage();
    return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;
  }
}
