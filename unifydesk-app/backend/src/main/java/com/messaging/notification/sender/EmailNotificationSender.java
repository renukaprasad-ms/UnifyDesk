package com.messaging.notification.sender;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.model.NotificationChannel;
import com.messaging.notification.platform.delivery.email.PlatformSesEmailClient;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

  private final PlatformSesEmailClient sesClient;
  private final NotificationProperties notificationProperties;

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public void send(SendNotificationRequest request) {
    String from = senderAddress(request.senderPurpose());
    if (!StringUtils.hasText(from)) {
      throw new ServiceUnavailableException("Platform email sender identity is not configured");
    }
    String text = request.multipartAlternative()
        ? request.textBody()
        : (request.html() ? Jsoup.parse(request.body()).text() : request.body());
    String html = request.multipartAlternative()
        ? request.htmlBody()
        : (request.html() ? request.body() : null);
    try {
      sesClient.send(from, request.recipient(), request.subject(), text, html);
    } catch (SesV2Exception | SdkClientException exception) {
      throw new ServiceUnavailableException("Unable to deliver platform email through SES", exception);
    }
  }

  private String senderAddress(String purpose) {
    var email = notificationProperties.getEmail();
    if (purpose == null || purpose.isBlank()) {
      return email.getNotificationFrom();
    }
    return switch (purpose.trim().toUpperCase(Locale.ROOT)) {
      case "OTP" -> email.getOtpFrom();
      case "SECURITY" -> email.getSecurityFrom();
      case "BILLING" -> email.getBillingFrom();
      case "NOTIFICATION" -> email.getNotificationFrom();
      default -> throw new IllegalArgumentException("Unsupported platform email sender purpose");
    };
  }
}
