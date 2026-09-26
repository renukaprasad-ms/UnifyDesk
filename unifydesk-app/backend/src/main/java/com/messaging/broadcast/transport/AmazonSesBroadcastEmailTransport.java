package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.managedemail.delivery.SesSendContext;
import com.messaging.managedemail.delivery.SesSendContextResolver;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.*;

@Component
@RequiredArgsConstructor
public class AmazonSesBroadcastEmailTransport implements BroadcastEmailTransport {

  private final SesSendContextResolver contextResolver;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.AMAZON_SES;
  }

  @Override
  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    SesSendContext context = contextResolver.resolve(connection, sender);
    Destination destination = Destination.builder().toAddresses(command.recipient()).build();
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
    Body.Builder body = Body.builder().text(text);
    if (command.bodyHtml() != null && !command.bodyHtml().isBlank()) {
      body.html(
          Content.builder()
              .data(command.bodyHtml())
              .charset(StandardCharsets.UTF_8.name())
              .build());
    }
    Message message = Message.builder().subject(subject).body(body.build()).build();

    SendEmailRequest.Builder request =
        SendEmailRequest.builder()
            .fromEmailAddress(fromAddress(sender, context))
            .destination(destination)
            .content(EmailContent.builder().simple(message).build())
            .emailTags(tags(context, command));
    if (sender.getReplyToAddress() != null && !sender.getReplyToAddress().isBlank()) {
      request.replyToAddresses(sender.getReplyToAddress());
    }
    if (context.managed()) {
      request
          .tenantName(context.tenantName())
          .configurationSetName(context.configurationSetName());
    }

    try {
      SendEmailResponse response = context.client().sendEmail(request.build());
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

  private List<MessageTag> tags(SesSendContext context, BroadcastEmailCommand command) {
    if (!context.managed()) {
      return List.of(tag("messaging_recipient", command.correlationId()));
    }
    List<MessageTag> tags = new ArrayList<>();
    tags.add(tag("ud_company_id", required(command.companyId(), "company")));
    tags.add(tag("ud_run_id", required(command.runId(), "run")));
    tags.add(tag("ud_sender_id", required(command.senderId(), "sender")));
    tags.add(tag("ud_domain_id", required(context.domainId(), "domain")));
    tags.add(tag("ud_recipient_id", required(command.recipientId(), "recipient")));
    tags.add(tag("ud_correlation", command.correlationId()));
    return tags;
  }

  private MessageTag tag(String name, String value) {
    if (value == null || value.isBlank()) {
      throw BroadcastTransportException.permanent(
          "MANAGED_EMAIL_CORRELATION_MISSING",
          "Managed Email send correlation is incomplete",
          null);
    }
    return MessageTag.builder().name(name).value(value).build();
  }

  private String required(Long value, String field) {
    if (value == null) {
      throw BroadcastTransportException.permanent(
          "MANAGED_EMAIL_CORRELATION_MISSING",
          "Managed Email " + field + " correlation is missing",
          null);
    }
    return value.toString();
  }

  private String fromAddress(BroadcastSender sender, SesSendContext context) {
    String address = context.managed() ? context.managedFromAddress() : sender.getFromAddress();
    if (sender.getDisplayName() == null || sender.getDisplayName().isBlank()) {
      return address;
    }
    String safeName = sender.getDisplayName().replace(""", "");
    return safeName + " <" + address + ">";
  }
}
