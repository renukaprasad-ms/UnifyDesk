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

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public void send(SendNotificationRequest request) {
    String from = notificationProperties.getEmail().getFrom();
    if (!StringUtils.hasText(from)) {
      throw new ServiceUnavailableException("Email sender is not configured");
    }

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(from);
      helper.setTo(request.recipient());
      helper.setSubject(request.subject());
      helper.setText(request.body(), request.html());
      mailSender.send(message);
    } catch (MessagingException | MailException exception) {
      throw new ServiceUnavailableException("Unable to send email", exception);
    }
  }
}
