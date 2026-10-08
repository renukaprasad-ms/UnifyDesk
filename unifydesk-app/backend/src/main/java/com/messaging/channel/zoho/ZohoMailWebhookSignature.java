package com.messaging.channel.zoho;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ZohoMailWebhookSignature {

  private ZohoMailWebhookSignature() {}

  public static boolean matches(String secret, String signature, byte[] rawBody) {
    if (secret == null || secret.isBlank() || signature == null
        || signature.isBlank() || rawBody == null) {
      return false;
    }
    try {
      byte[] supplied = Base64.getDecoder().decode(signature.trim());
      if (supplied.length != 32) return false;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return MessageDigest.isEqual(mac.doFinal(rawBody), supplied);
    } catch (Exception exception) {
      return false;
    }
  }
}
