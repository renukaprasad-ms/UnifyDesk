package com.messaging.notification.sender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTests {

  @Mock private JavaMailSender mailSender;

  @Test
  void sendUsesConfiguredSmtpSender() {
    NotificationProperties properties = new NotificationProperties();
    properties.getEmail().setFrom("noreply@example.com");
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    EmailNotificationSender sender = new EmailNotificationSender(mailSender, properties);

    sender.send(SendNotificationRequest.email("user@example.com", "Welcome", "Hello", false));

    verify(mailSender).send(mimeMessage);
  }

  @Test
  void sendFailsWhenFromAddressIsMissing() {
    EmailNotificationSender sender = new EmailNotificationSender(mailSender, new NotificationProperties());

    assertThatThrownBy(
            () ->
                sender.send(
                    SendNotificationRequest.email("user@example.com", "Welcome", "Hello", false)))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessage("Email sender is not configured");
  }
}
