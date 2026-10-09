package com.messaging.emailprovider.dkim.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DkimSigner {

  private static final List<String> CANDIDATE_HEADERS =
      List.of(
          "from",
          "to",
          "cc",
          "reply-to",
          "subject",
          "date",
          "message-id",
          "mime-version",
          "content-type",
          "content-transfer-encoding",
          "list-unsubscribe",
          "list-unsubscribe-post");

  public byte[] sign(byte[] rawMessage, String domain, String selector, PrivateKey privateKey) {
    try {
      String raw = new String(rawMessage, StandardCharsets.ISO_8859_1);
      int split = raw.indexOf("\r\n\r\n");
      if (split < 0) {
        throw new IllegalArgumentException("MIME message is missing the header/body separator");
      }

      String headerBlock = raw.substring(0, split);
      String body = raw.substring(split + 4);
      List<Header> headers = parseHeaders(headerBlock);
      String canonicalBody = canonicalizeBody(body);
      String bodyHash =
          Base64.getEncoder()
              .encodeToString(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonicalBody.getBytes(StandardCharsets.ISO_8859_1)));

      List<Header> signedHeaders = new ArrayList<>();
      for (String name : CANDIDATE_HEADERS) {
        headers.stream()
            .filter(header -> header.name().equalsIgnoreCase(name))
            .findFirst()
            .ifPresent(signedHeaders::add);
      }

      if (signedHeaders.stream().noneMatch(header -> header.name().equalsIgnoreCase("from"))) {
        throw new IllegalArgumentException("MIME message does not contain From header");
      }

      String headerNames =
          signedHeaders.stream()
              .map(header -> header.name().toLowerCase(Locale.ROOT))
              .reduce((a, b) -> a + ":" + b)
              .orElseThrow();

      String dkimValue =
          "v=1; a=rsa-sha256; c=relaxed/relaxed; d="
              + domain
              + "; s="
              + selector
              + "; h="
              + headerNames
              + "; bh="
              + bodyHash
              + "; b=";

      StringBuilder signingData = new StringBuilder();
      for (Header header : signedHeaders) {
        signingData.append(canonicalizeHeader(header.name(), header.value()));
      }
      signingData.append(canonicalizeHeader("DKIM-Signature", dkimValue));

      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(signingData.toString().getBytes(StandardCharsets.ISO_8859_1));
      String encodedSignature = Base64.getEncoder().encodeToString(signature.sign());

      String signedHeader = "DKIM-Signature: " + dkimValue + encodedSignature + "\r\n";
      return (signedHeader + raw).getBytes(StandardCharsets.ISO_8859_1);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to DKIM sign email", exception);
    }
  }

  private List<Header> parseHeaders(String block) {
    List<Header> result = new ArrayList<>();
    String currentName = null;
    StringBuilder currentValue = new StringBuilder();

    for (String line : block.split("\r\n")) {
      if ((line.startsWith(" ") || line.startsWith("\t")) && currentName != null) {
        currentValue.append(' ').append(line.trim());
        continue;
      }

      if (currentName != null) {
        result.add(new Header(currentName, currentValue.toString()));
      }

      int colon = line.indexOf(':');
      if (colon <= 0) {
        throw new IllegalArgumentException("Invalid MIME header");
      }
      currentName = line.substring(0, colon);
      currentValue = new StringBuilder(line.substring(colon + 1));
    }

    if (currentName != null) {
      result.add(new Header(currentName, currentValue.toString()));
    }
    return result;
  }

  private String canonicalizeHeader(String name, String value) {
    String normalized =
        value.replaceAll("\r?\n[ \\t]+", " ").trim().replaceAll("[ \\t]+", " ");
    return name.toLowerCase(Locale.ROOT) + ":" + normalized + "\r\n";
  }

  private String canonicalizeBody(String body) {
    String normalized = body.replace("\r\n", "\n").replace("\r", "\n");
    String[] lines = normalized.split("\n", -1);
    List<String> canonical = new ArrayList<>();
    for (String line : lines) {
      canonical.add(line.replaceAll("[ \\t]+", " ").replaceAll("[ \\t]+$", ""));
    }
    while (!canonical.isEmpty() && canonical.get(canonical.size() - 1).isEmpty()) {
      canonical.remove(canonical.size() - 1);
    }
    if (canonical.isEmpty()) {
      return "\r\n";
    }
    return String.join("\r\n", canonical) + "\r\n";
  }

  private record Header(String name, String value) {}
}
