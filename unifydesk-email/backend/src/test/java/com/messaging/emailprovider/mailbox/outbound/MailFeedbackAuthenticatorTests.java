package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

class MailFeedbackAuthenticatorTests {
  @Test
  void longDedicatedSecretIsRequiredAndComparedWithoutShortcut() {
    assertThrows(IllegalStateException.class,()->new MailFeedbackAuthenticator(""));
    assertThrows(IllegalStateException.class,()->new MailFeedbackAuthenticator("too-short"));
    String token="a-32-character-test-token-for-only-private-mta";
    var auth=new MailFeedbackAuthenticator(token);
    assertDoesNotThrow(()->auth.verify(token));
    assertThrows(UnauthorizedException.class,()->auth.verify(token+"X"));
    assertThrows(UnauthorizedException.class,()->auth.verify(null));
  }
}
