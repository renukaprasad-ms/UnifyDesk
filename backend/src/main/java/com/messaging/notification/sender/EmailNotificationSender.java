package com.messaging.notification.sender;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.model.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationSender implements NotificationSender {

  private final JavaMailSender mailSender;
  private final NotificationProperties notificationProperties;

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public void send(SendNotificationRequest request) {
    long startedAt = System.nanoTime();
    String from = notificationProperties.getEmail().getFrom();
    if (!StringUtils.hasText(from)) {
      throw new ServiceUnavailableException("Email sender is not configured");
    }

    try {
      long buildStartedAt = System.nanoTime();
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(from);
      helper.setTo(request.recipient());
      helper.setSubject(request.subject());
      helper.setText(request.body(), request.html());
      long smtpStartedAt = System.nanoTime();
      mailSender.send(message);
      log.info(
          "Email sent recipient={} buildDurationMs={} smtpDurationMs={} totalDurationMs={}",
          request.recipient(),
          elapsedMsBetween(buildStartedAt, smtpStartedAt),
          elapsedMs(smtpStartedAt),
          elapsedMs(startedAt));
    } catch (MessagingException | MailException exception) {
      log.error(
          "Email send failed recipient={} totalDurationMs={}",
          request.recipient(),
          elapsedMs(startedAt),
          exception);
      throw new ServiceUnavailableException("Unable to send email", exception);
    }
  }

  private long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private long elapsedMsBetween(long startedAt, long finishedAt) {
    return (finishedAt - startedAt) / 1_000_000;
  }
}
