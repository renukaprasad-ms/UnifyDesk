package com.messaging.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

  private Email email = new Email();

  @Getter
  @Setter
  public static class Email {
    private String sesRegion = "ap-southeast-2";
    private String otpFrom = "no-reply@unifydesk.in";
    private String securityFrom = "security@unifydesk.in";
    private String billingFrom = "billing@unifydesk.in";
    private String notificationFrom = "notification@unifydesk.in";
    private String brandName = "UnifyDesk";
    private String logoUrl;
    private String websiteUrl = "https://app.unifydesk.in";
    private String supportEmail;
    private String supportUrl;
    private String primaryColor = "#4f46e5";
  }
}
