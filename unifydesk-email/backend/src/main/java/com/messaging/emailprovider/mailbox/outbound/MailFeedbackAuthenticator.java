package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dedicated secret for MTA-originated mailbox feedback, never a user token. */
@Component
@ConditionalOnProperty(prefix = "app.mail.feedback", name = "enabled", havingValue = "true")
public class MailFeedbackAuthenticator {
  private final byte[] configured;

  public MailFeedbackAuthenticator(@Value("${app.mail.feedback.token:}") String token) {
    if (token == null || token.length() < 32) {
      throw new IllegalStateException("Dedicated mail feedback secret must be >=32 characters");
    }
    configured = token.getBytes(StandardCharsets.UTF_8);
  }

  public void verify(String supplied) {
    if (supplied == null
        || !MessageDigest.isEqual(configured, supplied.getBytes(StandardCharsets.UTF_8))) {
      throw new UnauthorizedException("Invalid mail feedback secret");
    }
  }
}
