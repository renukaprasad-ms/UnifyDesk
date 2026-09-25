package com.messaging.company.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.invitation")
public class CompanyInvitationProperties {

  private Duration expiry = Duration.ofHours(72);
  private String frontendBaseUrl = "https://app.unifydesk.in";
}
