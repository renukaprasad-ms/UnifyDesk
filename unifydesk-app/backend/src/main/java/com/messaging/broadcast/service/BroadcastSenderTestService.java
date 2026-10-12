package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastTestEmailResponse;
import com.messaging.broadcast.dto.SendBroadcastTestEmailRequest;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailResult;
import com.messaging.broadcast.transport.BroadcastEmailTransportRegistry;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.id.IdGenerator;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import com.messaging.emailtemplate.service.EmailTemplateProviderBindingService;
import com.messaging.emailtemplate.service.EmailTemplateService;
import jakarta.mail.internet.InternetAddress;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastSenderTestService {

  private final BroadcastSenderService senderService;
  private final BroadcastEmailTransportRegistry emailTransportRegistry;
  private final EmailTemplateService emailTemplateService;
  private final EmailTemplateProviderBindingService templateProviderBindingService;

  public BroadcastTestEmailResponse sendTestEmail(
      Long companyId, Long senderId, SendBroadcastTestEmailRequest request) {
    BroadcastSender sender = senderService.requireActive(companyId, senderId);
    // Independent UnifyDesk Email's public API currently enforces marketing opt-in.
    // A manually typed test address has no verified Contacts consent identity.
    if (sender.getProvider() == BroadcastSenderProvider.UNIFYDESK_EMAIL) {
      throw new BadRequestException(
          "Use an opted-in Contacts audience for UnifyDesk Email testing");
    }
    if (sender.getProvider().channelType() != ChannelType.EMAIL) {
      throw new BadRequestException("Test email is only available for email Broadcast providers");
    }

    BroadcastSenderConnection connection =
        senderService.requireConnection(companyId, sender.getConnectionId());
    String recipient = normalizeEmail(request.recipient());
    String correlationId = "test-" + IdGenerator.nextId();

    String subject;
    String body;
    String bodyHtml = null;
    String providerTemplateId = null;
    Map<String, String> variables = Map.of();

    if (sender.getProvider() == BroadcastSenderProvider.MSG91_EMAIL) {
      if (request.templateId() == null) {
        throw new BadRequestException("Choose a synced Email template for the MSG91 test");
      }
      EmailTemplateService.PublishedEmail template =
          emailTemplateService.requirePublishedForBroadcast(companyId, request.templateId());
      EmailTemplateProviderBinding binding =
          templateProviderBindingService.requireSendableForBroadcast(
              companyId,
              template.templateId(),
              template.version(),
              connection.getId());

      LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
      if (request.variables() != null) {
        request.variables().forEach(
            (key, value) -> {
              if (key != null && value != null) resolved.put(key, value.trim());
            });
      }
      resolved.putIfAbsent("email", recipient);
      resolved.putIfAbsent("destination", recipient);
      LocalDate today = LocalDate.now(ZoneOffset.UTC);
      resolved.putIfAbsent("current_date", today.toString());
      resolved.putIfAbsent("current_year", Integer.toString(today.getYear()));

      for (String variable : template.variables()) {
        String value = resolved.get(variable);
        if (value == null || value.isBlank()) {
          throw new BadRequestException(
              "Test value is required for template variable {{" + variable + "}}");
        }
        requireSafeProviderVariable(variable, value);
      }

      subject = template.subject();
      body = template.bodyText();
      bodyHtml = template.bodyHtml();
      providerTemplateId = binding.getProviderTemplateId();
      variables = Map.copyOf(resolved);
    } else {
      subject =
          request.subject() == null || request.subject().isBlank()
              ? "Messaging test email"
              : request.subject().trim();
      body =
          request.bodyText() == null || request.bodyText().isBlank()
              ? "This is a test email from Messaging. Your Broadcast provider connection is working."
              : request.bodyText().trim();
    }

    try {
      BroadcastEmailResult result =
          emailTransportRegistry
              .require(sender.getProvider())
              .send(
                  connection,
                  sender,
                  new BroadcastEmailCommand(
                      recipient,
                      subject,
                      body,
                      bodyHtml,
                      providerTemplateId,
                      variables,
                      null,
                      correlationId));
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

  private void requireSafeProviderVariable(String variable, String value) {
    if (value.contains("<") || value.contains(">")) {
      throw new BadRequestException(
          "Test value for {{" + variable + "}} must not contain HTML markup");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("javascript:")
        || normalized.startsWith("data:")
        || normalized.startsWith("vbscript:")) {
      throw new BadRequestException(
          "Test value for {{" + variable + "}} contains an unsafe URL scheme");
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
