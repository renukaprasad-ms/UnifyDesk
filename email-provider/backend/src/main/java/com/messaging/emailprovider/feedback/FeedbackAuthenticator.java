package com.messaging.emailprovider.feedback;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import com.messaging.emailprovider.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeedbackAuthenticator {

  private final String configuredToken;

  public FeedbackAuthenticator(
      @Value("${app.feedback.token:}") String configuredToken) {
    this.configuredToken = configuredToken;
  }

  public void verify(String providedToken) {
    if (configuredToken == null || configuredToken.isBlank()) {
      throw new ServiceUnavailableException("Feedback authentication is not configured");
    }
    if (providedToken == null
        || !MessageDigest.isEqual(
            configuredToken.getBytes(StandardCharsets.UTF_8),
            providedToken.getBytes(StandardCharsets.UTF_8))) {
      throw new UnauthorizedException("Invalid feedback token");
    }
  }
}
