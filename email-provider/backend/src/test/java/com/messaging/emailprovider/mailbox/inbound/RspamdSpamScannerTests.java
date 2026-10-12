package com.messaging.emailprovider.mailbox.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RspamdSpamScannerTests {
  private final JsonMapper mapper = JsonMapper.builder().build();

  private RspamdSpamScanner.Decision decide(String json) {
    return RspamdSpamScanner.classify(mapper.readTree(json));
  }

  @Test
  void onlyExplicitlyApprovedHamMayEnterInbox() {
    assertEquals(RspamdSpamScanner.Decision.INBOX,
        decide("{\"action\":\"no action\",\"score\":0.4,\"required_score\":6.0}"));
    assertEquals(RspamdSpamScanner.Decision.SPAM,
        decide("{\"action\":\"add header\",\"score\":7.2,\"required_score\":6.0}"));
    assertEquals(RspamdSpamScanner.Decision.QUARANTINE,
        decide("{\"action\":\"reject\",\"score\":15,\"required_score\":6}"));
  }

  @Test
  void missingOrSkippedScanNeverEntersInbox() {
    assertThrows(ServiceUnavailableException.class,
        () -> decide("{\"action\":\"no action\",\"is_skipped\":true,\"score\":0,\"required_score\":6}"));
    assertThrows(ServiceUnavailableException.class,
        () -> decide("{\"action\":\"unknown\",\"score\":0,\"required_score\":6}"));
    assertThrows(ServiceUnavailableException.class,
        () -> decide("{\"action\":\"no action\"}"));
  }
}
