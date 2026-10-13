package com.messaging.managedemail.control.service;

import com.google.common.net.InternetDomainName;
import com.messaging.common.exception.BadRequestException;
import java.net.IDN;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ManagedEmailDomainNameService {

  private static final Pattern ASCII_LABEL =
      Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
  private static final Pattern IPV4 =
      Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
  private static final Set<String> RESERVED =
      Set.of(
          "www",
          "api",
          "app",
          "cdn",
          "static",
          "assets",
          "dns",
          "ns",
          "mx",
          "mail",
          "email",
          "smtp",
          "imap",
          "pop",
          "dmarc",
          "bounce",
          "feedback",
          "postmaster",
          "admin",
          "administrator",
          "root",
          "security",
          "abuse",
          "auth",
          "oauth",
          "login",
          "dev",
          "test",
          "staging",
          "prod",
          "production",
          "unifydesk",
          "ses",
          "aws",
          "amazon",
          "support",
          "billing",
          "status",
          "help",
          "verify",
          "track",
          "click",
          "open",
          "unsubscribe");
  private static final Set<String> PROHIBITED =
      Set.of(
          "fuck",
          "fucking",
          "shit",
          "bitch",
          "cunt",
          "porn",
          "nazi",
          "phishing",
          "scam",
          "fraud");

  private static final char[] BASE32 = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();
  private final SecureRandom secureRandom = new SecureRandom();

  public String normalizeCustomerDomain(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException("Domain is required");
    }
    String value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
    if (value.contains("://")
        || value.contains("/")
        || value.contains("\\")
        || value.contains("@")
        || value.contains("*")
        || value.contains("?")
        || value.contains("#")
        || value.contains(":")
        || value.chars().anyMatch(Character::isWhitespace)) {
      throw new BadRequestException("Enter a bare domain without URL, path, email, or wildcard");
    }
    if (value.startsWith(".") || value.endsWith(".")) {
      throw new BadRequestException("Domain is invalid");
    }

    final String ascii;
    try {
      ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Domain is invalid");
    }

    if (ascii.length() > 253 || IPV4.matcher(ascii).matches()) {
      throw new BadRequestException("Domain is invalid");
    }
    String[] labels = ascii.split("\\.", -1);
    if (labels.length < 2) {
      throw new BadRequestException("A registrable domain is required");
    }
    for (String label : labels) {
      if (!ASCII_LABEL.matcher(label).matches()) {
        throw new BadRequestException("Domain contains an invalid label");
      }
    }
    try {
      InternetDomainName domain = InternetDomainName.from(ascii);
      if (!domain.hasPublicSuffix() || domain.isPublicSuffix()) {
        throw new BadRequestException("A registrable domain is required");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Domain is invalid");
    }
    return ascii;
  }

  public HostedName generateHostedName(String companyName, String baseDomain) {
    String prefix = readablePrefix(companyName);
    if (containsProhibitedToken(prefix)) {
      throw new BadRequestException("Company name cannot be used for a hosted sender domain");
    }
    if (RESERVED.contains(prefix)) {
      prefix = "company";
    }

    String suffix = randomBase32(6);
    int maxPrefix = Math.max(1, 63 - suffix.length() - 1);
    if (prefix.length() > maxPrefix) {
      prefix = prefix.substring(0, maxPrefix).replaceAll("-+$", "");
    }
    if (prefix.isBlank()) prefix = "company";

    String slug = prefix + "-" + suffix;
    String normalizedBase = normalizeBaseDomain(baseDomain);
    return new HostedName(slug, suffix, slug + "." + normalizedBase);
  }

  public String readablePrefix(String value) {
    String source = value == null ? "" : value;
    String decomposed = Normalizer.normalize(source, Normalizer.Form.NFKD);
    StringBuilder ascii = new StringBuilder();
    for (char ch : decomposed.toCharArray()) {
      if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;
      if (ch <= 127) ascii.append(ch);
    }
    String prefix =
        ascii.toString()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+|-+$", "");
    return prefix.isBlank() ? "company" : prefix;
  }

  public boolean containsProhibitedToken(String prefix) {
    String[] tokens = prefix.split("-");
    for (String token : tokens) {
      if (PROHIBITED.contains(token)) return true;
    }
    return false;
  }

  private String normalizeBaseDomain(String baseDomain) {
    if (baseDomain == null || baseDomain.isBlank()) {
      throw new IllegalStateException("Managed Email hosted base domain is not configured");
    }
    try {
      String value =
          IDN.toASCII(baseDomain.trim(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
      if (value.startsWith(".") || value.endsWith(".") || value.length() > 253) {
        throw new IllegalArgumentException();
      }
      return value;
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Managed Email hosted base domain is invalid", exception);
    }
  }

  public String validateHostedSubdomain(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException("Subdomain is required");
    }
    String clean = raw.trim().toLowerCase(Locale.ROOT);
    if (clean.length() < 2 || clean.length() > 63) {
      throw new BadRequestException("Subdomain must be between 2 and 63 characters");
    }
    if (!ASCII_LABEL.matcher(clean).matches()) {
      throw new BadRequestException(
          "Subdomain must contain only lowercase letters, numbers, and hyphens, and cannot begin or end with a hyphen");
    }
    if (RESERVED.contains(clean)) {
      throw new BadRequestException("The subdomain '" + clean + "' is reserved and cannot be used");
    }
    if (containsProhibitedToken(clean)) {
      throw new BadRequestException("The subdomain contains prohibited words");
    }
    return clean;
  }

  public String buildHostedDomain(String subdomain, String baseDomain) {
    String cleanSub = validateHostedSubdomain(subdomain);
    return cleanSub + "." + normalizeBaseDomain(baseDomain);
  }

  public String randomReservationToken() {
    return randomBase32(6);
  }

  private String randomBase32(int length) {
    char[] value = new char[length];
    for (int i = 0; i < length; i++) {
      value[i] = BASE32[secureRandom.nextInt(BASE32.length)];
    }
    return new String(value);
  }

  public record HostedName(String slug, String reservationToken, String domain) {}
}
