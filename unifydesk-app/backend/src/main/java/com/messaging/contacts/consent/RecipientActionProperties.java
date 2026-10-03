package com.messaging.contacts.consent;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.recipient-action")
public class RecipientActionProperties {

  private String encryptionKey;
  private Duration tokenTtl = Duration.ofDays(365);
  private Duration clockSkew = Duration.ofMinutes(5);
  private String frontendBaseUrl = "https://app.unifydesk.in";
  private String publicApiBaseUrl = "https://api.unifydesk.in";
}
