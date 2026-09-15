package com.messaging.security.web;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.cookie")
public class CookieProperties {

  private String accessName = "msid";
  private String refreshName = "msid_r";
  private Duration accessMaxAge = Duration.ofMinutes(15);
  private Duration refreshMaxAge = Duration.ofDays(7);
  private String path = "/";
  private String domain;
  private boolean httpOnly = true;
  private boolean secure;
  private String sameSite = "Strict";
}
