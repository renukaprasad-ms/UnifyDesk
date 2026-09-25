package com.messaging.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

  private boolean async = true;
  private Email email = new Email();

  @Getter
  @Setter
  public static class Email {
    private String from;
    private String brandName = "Messaging";
    private String logoUrl;
    private String websiteUrl = "https://messaging.renukaprasad.in";
    private String supportEmail;
    private String supportUrl;
    private String primaryColor = "#4f46e5";
  }
}
