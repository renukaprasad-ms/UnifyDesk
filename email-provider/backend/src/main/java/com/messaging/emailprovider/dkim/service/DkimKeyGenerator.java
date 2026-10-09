package com.messaging.emailprovider.dkim.service;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class DkimKeyGenerator {

  private static final int RSA_KEY_SIZE = 2048;

  public DkimKeyMaterial generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(RSA_KEY_SIZE);
      KeyPair pair = generator.generateKeyPair();

      return new DkimKeyMaterial(
          Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
          Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to generate DKIM key pair", exception);
    }
  }
}
