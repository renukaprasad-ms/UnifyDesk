package com.messaging.emailprovider.dkim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DkimKeyGeneratorTests {

  private final DkimKeyGenerator generator = new DkimKeyGenerator();

  @Test
  void generatesRsa2048KeyPair() throws Exception {
    DkimKeyMaterial material = generator.generate();

    RSAPrivateKey privateKey =
        (RSAPrivateKey)
            KeyFactory.getInstance("RSA")
                .generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(material.privateKey())));
    RSAPublicKey publicKey =
        (RSAPublicKey)
            KeyFactory.getInstance("RSA")
                .generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(material.publicKey())));

    assertNotNull(privateKey);
    assertNotNull(publicKey);
    assertEquals(2048, publicKey.getModulus().bitLength());
    assertEquals(publicKey.getModulus(), privateKey.getModulus());
  }
}
