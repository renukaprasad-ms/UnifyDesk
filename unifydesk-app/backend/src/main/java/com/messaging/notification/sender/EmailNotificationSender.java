package com.messaging.notification.sender;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.model.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

  private final JavaMailSender mailSender;
  private final NotificationProperties notificationProperties;

  private String senderAddress(String purpose) {
    var email = notificationProperties.getEmail();
    if (purpose == null || purpose.isBlank()) {
      return StringUtils.hasText(email.getNotificationFrom()) ? email.getNotificationFrom() : email.getFrom();
    }
    return switch (purpose.toUpperCase(java.util.Locale.ROOT)) {
      case "OTP" -> email.getOtpFrom();
      case "SECURITY" -> email.getSecurityFrom();
      case "BILLING" -> email.getBillingFrom();
      case "NOTIFICATION" -> email.getNotificationFrom();
      default -> throw new ServiceUnavailableException("Unsupported email sender purpose");
    };
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public void send(SendNotificationRequest request) {
    String from = senderAddress(request.senderPurpose());
    if (!StringUtils.hasText(from)) {
      throw new ServiceUnavailableException("Email sender is not configured");
    }

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, request.multipartAlternative(), "UTF-8");
      helper.setFrom(from);
      helper.setTo(request.recipient());
      helper.setSubject(request.subject());
      if (request.multipartAlternative()) {
        helper.setText(request.textBody(), request.htmlBody());
      } else {
        helper.setText(request.body(), request.html());
      }
      mailSender.send(message);
    } catch (MessagingException | MailException exception) {
      throw new ServiceUnavailableException("Unable to send email", exception);
    }
  }
}
