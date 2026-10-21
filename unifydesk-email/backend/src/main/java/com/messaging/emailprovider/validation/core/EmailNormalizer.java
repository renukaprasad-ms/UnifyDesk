package com.messaging.emailprovider.validation.core;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

  private static final Pattern LOCAL_PART = Pattern.compile("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+$");

  public Optional<NormalizedEmail> normalize(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = raw.trim();
    if (value.isEmpty() || value.length() > 320) {
      return Optional.empty();
    }
    int at = value.lastIndexOf('@');
    if (at <= 0 || at == value.length() - 1 || value.indexOf('@') != at) {
      return Optional.empty();
    }

    String local = value.substring(0, at);
    String domain;
    try {
      domain =
          IDN.toASCII(value.substring(at + 1), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }

    if (local.length() > 64
        || domain.length() > 253
        || !LOCAL_PART.matcher(local).matches()
        || local.startsWith(".")
        || local.endsWith(".")
        || local.contains("..")
        || domain.startsWith(".")
        || domain.endsWith(".")
        || !domain.contains(".")) {
      return Optional.empty();
    }

    String normalized = local + "@" + domain;
    return normalized.length() <= 320
        ? Optional.of(new NormalizedEmail(normalized, local, domain))
        : Optional.empty();
  }

  public String dedupeKey(String raw) {
    String fallback = raw == null ? "" : raw.trim();
    String canonical = normalize(raw).map(NormalizedEmail::value).orElse(fallback);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
