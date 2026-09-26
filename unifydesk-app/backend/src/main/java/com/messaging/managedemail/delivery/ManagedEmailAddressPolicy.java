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
    if (!isStrictPlainMailbox(address)) {
      return null;
    }
    int at = address.indexOf('@');
    return normalizeDomain(address.substring(at + 1));
  }

  private boolean isStrictPlainMailbox(String address) {
    if (address == null
        || address.isBlank()
        || !address.equals(address.trim())
        || address.length() > 320
        || address.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch))
        || containsMailboxWrapperSyntax(address)) {
      return false;
    }

    int at = address.indexOf('@');
    if (at <= 0 || at != address.lastIndexOf('@') || at == address.length() - 1) {
      return false;
    }

    String localPart = address.substring(0, at);
    if (localPart.length() > 64
        || localPart.startsWith(".")
        || localPart.endsWith(".")
        || localPart.contains("..")) {
      return false;
    }

    try {
      InternetAddress[] parsed = InternetAddress.parse(address, true);
      if (parsed.length != 1) {
        return false;
      }
      InternetAddress mailbox = parsed[0];
      mailbox.validate();
      return mailbox.getPersonal() == null
          && address.equals(mailbox.getAddress())
          && normalizeDomain(address.substring(at + 1)) != null;
    } catch (Exception exception) {
      return false;
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
