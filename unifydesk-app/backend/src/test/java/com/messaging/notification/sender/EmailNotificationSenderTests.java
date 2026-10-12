package com.messaging.notification.sender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.platform.delivery.email.PlatformSesEmailClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTests {

  @Mock private PlatformSesEmailClient sesClient;

  @Test
  void otpUsesNoReplySesIdentity() {
    var sender = new EmailNotificationSender(sesClient, new NotificationProperties());
    sender.send(SendNotificationRequest.email("user@example.com", "Verify", "123456", false)
        .withSenderPurpose("OTP"));

    verify(sesClient).send("no-reply@unifydesk.in", "user@example.com", "Verify", "123456", null);
  }

  @Test
  void passwordResetUsesSecuritySesIdentity() {
    var sender = new EmailNotificationSender(sesClient, new NotificationProperties());
    sender.send(SendNotificationRequest.email("user@example.com", "Reset password", "123456", false)
        .withSenderPurpose("SECURITY"));

    verify(sesClient).send("security@unifydesk.in", "user@example.com", "Reset password", "123456", null);
  }

  @Test
  void billingUsesBillingSesIdentity() {
    var sender = new EmailNotificationSender(sesClient, new NotificationProperties());
    sender.send(SendNotificationRequest.billingEmail("user@example.com", "Invoice", "Paid", false));

    verify(sesClient).send("billing@unifydesk.in", "user@example.com", "Invoice", "Paid", null);
  }

  @Test
  void generalNotificationUsesNotificationSesIdentity() {
    var sender = new EmailNotificationSender(sesClient, new NotificationProperties());
    sender.send(SendNotificationRequest.email("user@example.com", "Welcome", "Hello", false));

    verify(sesClient).send("notification@unifydesk.in", "user@example.com", "Welcome", "Hello", null);
  }

  @Test
  void missingConfiguredFromFailsClosed() {
    NotificationProperties props = new NotificationProperties();
    props.getEmail().setSecurityFrom("");
    var sender = new EmailNotificationSender(sesClient, props);

    assertThatThrownBy(() -> sender.send(
        SendNotificationRequest.securityEmail("user@example.com", "Alert", "Review", false)))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}
