package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ZohoMailWebhookSignatureTests {

  @Test
  void validatesSignatureOverExactRawBytes() throws Exception {
    String secret = "zoho-webhook-secret-from-first-registration";
    byte[] payload = "{\"messageId\":123,\"subject\":\"Hello\"}".getBytes(StandardCharsets.UTF_8);
    String signature = sign(secret, payload);

    assertThat(ZohoMailWebhookSignature.matches(secret, signature, payload)).isTrue();
    assertThat(ZohoMailWebhookSignature.matches(secret, signature,
        "{\"subject\":\"Hello\",\"messageId\":123}".getBytes(StandardCharsets.UTF_8)))
        .isFalse();
    assertThat(ZohoMailWebhookSignature.matches(secret, signature,
        "{\"messageId\":123,\"subject\":\"Tampered\"}".getBytes(StandardCharsets.UTF_8)))
        .isFalse();
  }

  @Test
  void rejectsWrongSecretAndMalformedSignatures() throws Exception {
    byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
    String signature = sign("correct-secret", payload);
    assertThat(ZohoMailWebhookSignature.matches("wrong-secret", signature, payload)).isFalse();
    assertThat(ZohoMailWebhookSignature.matches("correct-secret", "not-base64!", payload)).isFalse();
    assertThat(ZohoMailWebhookSignature.matches("correct-secret", "", payload)).isFalse();
    assertThat(ZohoMailWebhookSignature.matches(null, signature, payload)).isFalse();
    assertThat(ZohoMailWebhookSignature.matches("correct-secret", signature, null)).isFalse();
  }

  private String sign(String secret, byte[] payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(payload));
  }
}
