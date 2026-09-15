package com.messaging.security.jwt;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

  private String issuer = "messaging";
  private String privateKey;
  private String publicKey;
  private Duration accessExpiration = Duration.ofMinutes(15);
  private Duration refreshExpiration = Duration.ofDays(7);
}
