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
  }
}
