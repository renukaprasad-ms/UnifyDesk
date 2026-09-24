package com.messaging.emailprovider.dkim.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DkimSignerTests {

  private final DkimKeyGenerator generator = new DkimKeyGenerator();
  private final DkimSigner signer = new DkimSigner();

  @Test
  void prependsDkimHeaderWithoutChangingOriginalMessage() throws Exception {
    DkimKeyMaterial material = generator.generate();
    var privateKey =
        KeyFactory.getInstance("RSA")
            .generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(material.privateKey())));

    byte[] raw =
        ("From: sender@example.com\r\n"
                + "To: user@example.net\r\n"
                + "Subject: Test\r\n"
                + "Date: Thu, 24 Sep 2026 08:00:00 GMT\r\n"
                + "Message-ID: <1@example.com>\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + "SGVsbG8=\r\n")
            .getBytes(StandardCharsets.ISO_8859_1);

    byte[] signed = signer.sign(raw, "example.com", "rly1", privateKey);
    String value = new String(signed, StandardCharsets.ISO_8859_1);

    assertTrue(value.startsWith("DKIM-Signature: v=1; a=rsa-sha256;"));
    assertTrue(value.contains("; d=example.com; s=rly1;"));
    byte[] suffix = java.util.Arrays.copyOfRange(signed, signed.length - raw.length, signed.length);
    assertArrayEquals(raw, suffix);
  }
}
