package com.messaging.auth.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.auth")
public class AuthSecurityProperties {

  private int loginIpLimit = 120;
  private int loginIdentifierLimit = 10;
  private int registerIpLimit = 30;
  private int registerIdentifierLimit = 5;
  private int otpVerifyLimit = 5;
}
