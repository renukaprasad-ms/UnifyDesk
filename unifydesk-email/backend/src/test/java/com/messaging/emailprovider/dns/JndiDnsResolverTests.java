package com.messaging.emailprovider.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JndiDnsResolverTests {

  @Test
  void normalizesQuotedTxtChunks() {
    assertEquals(
        "v=DKIM1; k=rsa; p=PUBLICKEY",
        JndiDnsResolver.normalizeTxtValue("\"v=DKIM1; k=rsa;\" \"p=PUBLICKEY\""));
  }
}
