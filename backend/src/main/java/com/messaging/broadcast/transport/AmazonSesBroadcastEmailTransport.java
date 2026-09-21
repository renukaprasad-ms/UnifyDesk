package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.ses.AmazonSesClientManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.*;

@Component
@RequiredArgsConstructor
public class AmazonSesBroadcastEmailTransport implements BroadcastEmailTransport {

  private final AmazonSesClientManager clientManager;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.AMAZON_SES;
  }

  @Override
  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    Destination destination =
        Destination.builder().toAddresses(command.recipient()).build();
    Content subject =
        Content.builder()
            .data(command.subject() == null ? "" : command.subject())
            .charset(StandardCharsets.UTF_8.name())
            .build();
    Content text =
        Content.builder()
            .data(command.bodyText())
            .charset(StandardCharsets.UTF_8.name())
            .build();
    Message message =
        Message.builder()
            .subject(subject)
            .body(Body.builder().text(text).build())
            .build();

    SendEmailRequest.Builder request =
        SendEmailRequest.builder()
            .fromEmailAddress(fromAddress(sender))
            .destination(destination)
            .content(EmailContent.builder().simple(message).build())
            .emailTags(
                MessageTag.builder()
                    .name("messaging_recipient")
                    .value(command.correlationId())
                    .build());
    if (sender.getReplyToAddress() != null && !sender.getReplyToAddress().isBlank()) {
      request.replyToAddresses(sender.getReplyToAddress());
    }

    try {
      SendEmailResponse response =
          clientManager.client(connection).sendEmail(request.build());
      return new BroadcastEmailResult(response.messageId(), null, Instant.now());
    } catch (SesV2Exception exception) {
      int status = exception.statusCode();
      String code =
          exception.awsErrorDetails() == null
              ? "SES_REJECTED"
              : exception.awsErrorDetails().errorCode();
      String messageText =
          exception.awsErrorDetails() == null
              ? exception.getMessage()
              : exception.awsErrorDetails().errorMessage();
      if (status == 429 || status >= 500) {
        throw BroadcastTransportException.retryable(code, messageText, exception);
      }
      throw BroadcastTransportException.permanent(code, messageText, exception);
    } catch (SdkClientException exception) {
      throw BroadcastTransportException.unknown(
          "SES_SEND_UNKNOWN", "Amazon SES send outcome could not be confirmed", exception);
    }
  }

  private String fromAddress(BroadcastSender sender) {
    if (sender.getDisplayName() == null || sender.getDisplayName().isBlank()) {
      return sender.getFromAddress();
    }
    String safeName = sender.getDisplayName().replace("\"", "");
    return safeName + " <" + sender.getFromAddress() + ">";
  }
}
