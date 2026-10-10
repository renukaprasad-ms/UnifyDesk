package com.messaging.notification.platform.delivery.email;

import com.messaging.notification.config.NotificationProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RenderedEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

/** SES API-only gateway for persisted platform notification delivery attempts. */
@Component
@RequiredArgsConstructor
public class SesProductEmailGateway implements ProductEmailGateway {

  private final PlatformSesEmailClient sesClient;
  private final NotificationProperties notificationProperties;

  @Override
  public EmailSendResult send(String recipient, RenderedEmail email) {
    String from = notificationProperties.getEmail().getNotificationFrom();
    if (!StringUtils.hasText(from)) {
      return EmailSendResult.permanentFailure("PLATFORM_SENDER_NOT_CONFIGURED");
    }
    try {
      sesClient.send(from, recipient, email.subject(), email.textBody(), email.htmlBody());
      return EmailSendResult.accepted();
    } catch (SesV2Exception exception) {
      int status = exception.statusCode();
      if (status == 429 || status == 408 || status >= 500) {
        return EmailSendResult.transientFailure("SES_TEMPORARY_REJECTION");
      }
      return EmailSendResult.permanentFailure("SES_PERMANENT_REJECTION");
    } catch (SdkClientException exception) {
      // An SDK network failure may occur after SES accepts a message.
      // The attempt must not be blindly retried and produce duplicates.
      return EmailSendResult.unknown("SES_OUTCOME_UNKNOWN");
    } catch (IllegalArgumentException exception) {
      return EmailSendResult.permanentFailure("INVALID_EMAIL_REQUEST");
    }
  }
}
