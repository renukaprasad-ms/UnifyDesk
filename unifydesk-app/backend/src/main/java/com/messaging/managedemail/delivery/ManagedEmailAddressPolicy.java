package com.messaging.managedemail.delivery;

import java.net.IDN;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ManagedEmailAddressPolicy {

  public boolean matchesBoundDomain(String fromAddress, String normalizedDomain) {
    String domain = extractNormalizedDomain(fromAddress);
    String bound = normalizeDomain(normalizedDomain);
    return domain != null && bound != null && domain.equals(bound);
  }

  public String extractNormalizedDomain(String address) {
    if (address == null) return null;
    String value = address.trim();
    if (value.isEmpty()
        || value.indexOf('<') >= 0
        || value.indexOf('>') >= 0
        || value.chars().anyMatch(Character::isWhitespace)) {
      return null;
    }
    int at = value.indexOf('@');
    if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
      return null;
    }
    return normalizeDomain(value.substring(at + 1));
  }

  private String normalizeDomain(String domain) {
    if (domain == null) return null;
    String value = domain.trim();
    while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
    if (value.isEmpty()) return null;
    try {
      return IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
