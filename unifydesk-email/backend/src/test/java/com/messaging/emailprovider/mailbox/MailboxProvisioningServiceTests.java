package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class MailboxProvisioningServiceTests {
  @Test
  void normalizesMailDomains() {
    assertEquals("example.com", MailboxProvisioningService.normalizeDomain("Example.COM"));
    assertEquals("xn--bcher-kva.de", MailboxProvisioningService.normalizeDomain("bücher.de"));
  }

  @Test
  void rejectsUnsafeDomainNames() {
    for (String value :
        new String[] {
          "localhost",
          "-bad.example",
          "bad..example",
          "example.com.",
          " example.com",
          "exa mple.com",
          "bad_.example.com"
        }) {
      assertThrows(
          BadRequestException.class,
          () -> MailboxProvisioningService.normalizeDomain(value),
          value);
    }
  }

  @Test
  void mailboxAddressIsConservativeAndCanonical() {
    assertEquals("support+alerts", MailboxProvisioningService.normalizeLocalPart("Support+Alerts"));
    for (String value :
        new String[] {"", ".admin", "a..b", "a.", "bad space", "foo@bar", "with/slash", "üser"}) {
      assertThrows(
          BadRequestException.class,
          () -> MailboxProvisioningService.normalizeLocalPart(value),
          value);
    }
  }
}
