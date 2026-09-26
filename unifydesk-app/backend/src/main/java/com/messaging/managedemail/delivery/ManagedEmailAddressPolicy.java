package com.messaging.managedemail.delivery;

import jakarta.mail.internet.InternetAddress;
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
    if (address == null
        || address.isBlank()
        || !address.equals(address.trim())
        || address.length() > 320
        || address.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch))
        || containsMailboxWrapperSyntax(address)) {
      return null;
    }

    int at = address.indexOf('@');
    if (at <= 0 || at != address.lastIndexOf('@') || at == address.length() - 1) {
      return null;
    }

    String localPart = address.substring(0, at);
    String normalizedDomain = normalizeDomain(address.substring(at + 1));
    if (normalizedDomain == null
        || localPart.length() > 64
        || localPart.startsWith(".")
        || localPart.endsWith(".")
        || localPart.contains("..")) {
      return null;
    }

    String normalizedMailbox = localPart + "@" + normalizedDomain;
    try {
      InternetAddress[] parsed = InternetAddress.parse(normalizedMailbox, true);
      if (parsed.length != 1) {
        return null;
      }
      InternetAddress mailbox = parsed[0];
      mailbox.validate();
      if (mailbox.getPersonal() != null || !normalizedMailbox.equals(mailbox.getAddress())) {
        return null;
      }
      return normalizedDomain;
    } catch (Exception exception) {
      return null;
    }
  }

  private boolean containsMailboxWrapperSyntax(String address) {
    return address.indexOf('<') >= 0
        || address.indexOf('>') >= 0
        || address.indexOf('"') >= 0
        || address.indexOf('(') >= 0
        || address.indexOf(')') >= 0
        || address.indexOf(',') >= 0
        || address.indexOf(';') >= 0
        || address.indexOf('\\') >= 0
        || address.indexOf('[') >= 0
        || address.indexOf(']') >= 0;
  }

  private String normalizeDomain(String domain) {
    if (domain == null
        || domain.isBlank()
        || !domain.equals(domain.trim())
        || domain.startsWith(".")
        || domain.endsWith(".")
        || domain.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch))) {
      return null;
    }
    try {
      String ascii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
      if (ascii.isEmpty() || ascii.length() > 253) {
        return null;
      }
      String[] labels = ascii.split("\\.", -1);
      for (String label : labels) {
        if (label.isEmpty()
            || label.length() > 63
            || label.startsWith("-")
            || label.endsWith("-")) {
          return null;
        }
      }
      return ascii;
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
