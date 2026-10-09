package com.messaging.notification.platform.delivery.email;

import com.messaging.managedemail.delivery.ManagedSesClientProvider;
import com.messaging.notification.config.NotificationProperties;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * First-party transactional email transport. Uses SES v2 HTTPS API and the AWS default
 * credential chain; it must never fall back to an SMTP account.
 */
@Component
@RequiredArgsConstructor
public class PlatformSesEmailClient {

  private final ManagedSesClientProvider sesClients;
  private final NotificationProperties notificationProperties;

  public String send(String from, String to, String subject, String textBody, String htmlBody) {
    String region = notificationProperties.getEmail().getSesRegion();
    if (!StringUtils.hasText(region)) {
      throw new IllegalStateException("Platform SES region is not configured");
    }
    if (!StringUtils.hasText(from) || !StringUtils.hasText(to)
        || !StringUtils.hasText(subject)
        || (!StringUtils.hasText(textBody) && !StringUtils.hasText(htmlBody))) {
      throw new IllegalArgumentException("Platform email has missing required fields");
    }

    Body.Builder body = Body.builder();
    if (StringUtils.hasText(textBody)) {
      body.text(utf8(textBody));
    }
    if (StringUtils.hasText(htmlBody)) {
      body.html(utf8(htmlBody));
    }

    SendEmailRequest request = SendEmailRequest.builder()
        .fromEmailAddress(from)
        .destination(Destination.builder().toAddresses(to).build())
        .content(EmailContent.builder()
            .simple(Message.builder().subject(utf8(subject)).body(body.build()).build())
            .build())
        .build();
    return sesClients.client(region.trim()).sendEmail(request).messageId();
  }

  private Content utf8(String value) {
    return Content.builder().data(value).charset(StandardCharsets.UTF_8.name()).build();
  }
}
