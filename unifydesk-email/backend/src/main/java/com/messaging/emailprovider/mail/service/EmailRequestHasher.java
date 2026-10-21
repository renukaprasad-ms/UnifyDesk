package com.messaging.emailprovider.mail.service;

import com.messaging.emailprovider.mail.dto.SendEmailRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmailRequestHasher {

  public String hash(SendEmailRequest request) {
    String canonical =
        request.sendingDomainId()
            + "\n"
            + value(request.from())
            + "\n"
            + value(request.replyTo())
            + "\n"
            + list(request.to())
            + "\n"
            + list(request.cc())
            + "\n"
            + list(request.bcc())
            + "\n"
            + value(request.subject())
            + "\n"
            + value(request.text())
            + "\n"
            + value(request.html())
            + "\\n"
            + request.marketing()
            + "\\n"
            + value(request.consentReference());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private String list(List<String> values) {
    if (values == null) {
      return "";
    }
    return values.stream().map(this::value).sorted().reduce((a, b) -> a + "," + b).orElse("");
  }

  private String value(String value) {
    return value == null ? "" : value.trim();
  }
}
