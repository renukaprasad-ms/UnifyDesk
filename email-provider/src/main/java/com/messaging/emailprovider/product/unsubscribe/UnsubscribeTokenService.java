package com.messaging.emailprovider.product.unsubscribe;

import com.messaging.emailprovider.common.exception.BadRequestException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UnsubscribeTokenService {
  private final byte[] secret;
  private final String publicBaseUrl;

  public UnsubscribeTokenService(
      @Value("${app.product.unsubscribe.signing-key:}") String key,
      @Value("${app.product.unsubscribe.public-base-url:https://email.unifydesk.in}") String base) {
    this.secret = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    URI uri = URI.create(base);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
        || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException("Unsubscribe public URL must be a valid HTTPS origin");
    }
    this.publicBaseUrl = uri.getScheme() + "://" + uri.getHost();
  }

  public void requireConfigured() {
    if (secret.length < 32) {
      throw new BadRequestException("Email unsubscribe signing is not configured");
    }
  }

  public String sign(Long messageId, Long recipientId) {
    requireConfigured();
    if (messageId == null || recipientId == null || messageId <= 0 || recipientId <= 0) {
      throw new BadRequestException("Invalid unsubscribe reference");
    }
    String payload = messageId + "." + recipientId;
    return payload + "." + mac(payload);
  }

  public record Verified(Long messageId, Long recipientId) {}

  public Verified verify(String token) {
    requireConfigured();
    if (token == null || token.length() > 200
        || !token.matches("[0-9]+\\.[0-9]+\\.[a-f0-9]{64}")) {
      throw new BadRequestException("Invalid unsubscribe link");
    }
    int last = token.lastIndexOf('.');
    String payload = token.substring(0, last);
    byte[] expected = mac(payload).getBytes(StandardCharsets.US_ASCII);
    byte[] received = token.substring(last + 1).getBytes(StandardCharsets.US_ASCII);
    if (!MessageDigest.isEqual(expected, received)) {
      throw new BadRequestException("Invalid unsubscribe link");
    }
    try {
      String[] values = payload.split("\\.");
      return new Verified(Long.parseLong(values[0]), Long.parseLong(values[1]));
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid unsubscribe link");
    }
  }

  public String url(Long messageId, Long recipientId) {
    return publicBaseUrl + "/v1/unsubscribe/" + sign(messageId, recipientId);
  }

  private String mac(String payload) {
    try {
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(
          hmac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception error) {
      throw new IllegalStateException("Unsubscribe signature unavailable", error);
    }
  }
}
