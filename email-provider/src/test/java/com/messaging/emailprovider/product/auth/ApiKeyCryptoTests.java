package com.messaging.emailprovider.product.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiKeyCryptoTests {
  @Test
  void onlyMatchingTokenAuthenticates() {
    String token = "ude_live_" + ApiKeyCrypto.random(9) + "_" + ApiKeyCrypto.random(32);
    String hash = ApiKeyCrypto.hash(token);
    assertTrue(ApiKeyCrypto.matches(token, hash));
    assertFalse(ApiKeyCrypto.matches(token + "x", hash));
    assertFalse(hash.contains(token));
  }

  @Test
  void keysAreUnique() {
    assertFalse(ApiKeyCrypto.random(32).equals(ApiKeyCrypto.random(32)));
  }
}
