package com.messaging.emailprovider.mailbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MailDnsChallengeTests {
  @Test
  void acceptsExactDnsProofIncludingQuotedTxt() {
    String token = ApiKeyCrypto.random(32);
    String hash = ApiKeyCrypto.hash(token);
    assertTrue(
        MailboxProvisioningService.matchesChallenge(
            Set.of("\"ud-mail-verification=" + token + "\""), hash));
    assertFalse(
        MailboxProvisioningService.matchesChallenge(
            Set.of("ud-mail-verification=" + ApiKeyCrypto.random(32)), hash));
    assertFalse(MailboxProvisioningService.matchesChallenge(Set.of("other-key=" + token), hash));
  }

  @Test
  void rejectsAbsentProof() {
    assertFalse(MailboxProvisioningService.matchesChallenge(Set.of(), ApiKeyCrypto.hash("secret")));
  }
}
