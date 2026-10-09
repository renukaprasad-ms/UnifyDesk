package com.messaging.notification.platform.delivery.email;

import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RenderedEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JavaMailProductEmailGateway implements ProductEmailGateway {

  private final JavaMailSender mailSender;
  private final NotificationProperties notificationProperties;

  @Override
  public EmailSendResult send(String recipient, RenderedEmail email) {
    String from = notificationProperties.getEmail().getNotificationFrom();
    if (!StringUtils.hasText(from)) {
      return EmailSendResult.permanentFailure("PLATFORM_SENDER_NOT_CONFIGURED");
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(recipient);
      helper.setSubject(email.subject());
      helper.setText(email.textBody(), email.htmlBody());
      mailSender.send(message);
      return EmailSendResult.accepted();
    } catch (MailAuthenticationException exception) {
      return EmailSendResult.permanentFailure("SMTP_AUTHENTICATION_FAILED");
    } catch (MailSendException exception) {
      return classify(exception);
    } catch (MessagingException exception) {
      return EmailSendResult.permanentFailure("EMAIL_MESSAGE_INVALID");
    } catch (MailException exception) {
      return hasCause(exception, ConnectException.class)
          ? EmailSendResult.transientFailure("SMTP_CONNECT_FAILED")
          : EmailSendResult.transientFailure("SMTP_TRANSPORT_FAILED");
    }
  }

  private EmailSendResult classify(MailSendException exception) {
    if (hasCause(exception, SocketTimeoutException.class)) {
      return EmailSendResult.unknown("SMTP_AMBIGUOUS_TIMEOUT");
    }
    if (hasCause(exception, ConnectException.class)) {
      return EmailSendResult.transientFailure("SMTP_CONNECT_FAILED");
    }
    Throwable current = exception;
    while (current != null) {
      if (current instanceof SendFailedException sendFailedException) {
        Exception next = sendFailedException.getNextException();
        if (next != null) {
          String message = safeLower(next.getMessage());
          if (message.contains(" 4") || message.startsWith("4")) {
            return EmailSendResult.transientFailure("SMTP_TEMPORARY_REJECTION");
          }
          if (message.contains(" 5") || message.startsWith("5")) {
            return EmailSendResult.permanentFailure("SMTP_PERMANENT_REJECTION");
          }
        }
      }
      current = current.getCause();
    }
    return EmailSendResult.unknown("SMTP_AMBIGUOUS_SEND_FAILURE");
  }

  private String safeLower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    Throwable current = throwable;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
