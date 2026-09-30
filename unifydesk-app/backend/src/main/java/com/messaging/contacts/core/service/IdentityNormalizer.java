package com.messaging.contacts.core.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.exception.ContactCoreException;
import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IdentityNormalizer {

  private static final int MAX_IDENTITY_LENGTH = 320;
  private static final int MAX_LOCAL_PART_LENGTH = 64;
  private static final int MAX_NAMESPACE_LENGTH = 120;
  private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");
  private static final Pattern DOMAIN_LABEL =
      Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

  public NormalizedIdentity normalize(
      IdentityType type, String namespace, String value, String countryContext) {
    if (type == null || value == null) {
      throw ContactCoreException.invalidIdentity();
    }
    return switch (type) {
      case EMAIL -> normalizeEmail(namespace, value);
      case PHONE -> normalizePhone(namespace, value, countryContext);
      case EXTERNAL -> normalizeExternal(namespace, value);
    };
  }

  private NormalizedIdentity normalizeEmail(String namespace, String value) {
    requireNoNamespace(namespace);
    String display = normalizeDisplay(value);
    if (display.length() > MAX_IDENTITY_LENGTH || containsControlOrWhitespace(display)) {
      throw ContactCoreException.invalidIdentity();
    }

    int at = display.lastIndexOf('@');
    if (at <= 0 || at != display.indexOf('@') || at == display.length() - 1) {
      throw ContactCoreException.invalidIdentity();
    }

    String local = Normalizer.normalize(display.substring(0, at), Normalizer.Form.NFC);
    String unicodeDomain = display.substring(at + 1);
    if (local.isBlank()
        || local.length() > MAX_LOCAL_PART_LENGTH
        || local.startsWith(".")
        || local.endsWith(".")
        || local.contains("..")) {
      throw ContactCoreException.invalidIdentity();
    }

    String asciiDomain;
    try {
      asciiDomain = IDN.toASCII(unicodeDomain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      throw ContactCoreException.invalidIdentity();
    }
    if (!validDomain(asciiDomain)) {
      throw ContactCoreException.invalidIdentity();
    }

    String normalized = local.toLowerCase(Locale.ROOT) + "@" + asciiDomain;
    if (normalized.length() > MAX_IDENTITY_LENGTH) {
      throw ContactCoreException.invalidIdentity();
    }
    return new NormalizedIdentity(IdentityType.EMAIL, null, display, normalized);
  }

  private NormalizedIdentity normalizePhone(
      String namespace, String value, String countryContext) {
    requireNoNamespace(namespace);
    String display = normalizeDisplay(value);
    String region = null;
    if (!display.startsWith("+")) {
      if (countryContext == null || countryContext.isBlank()) {
        throw ContactCoreException.invalidIdentity();
      }
      region = countryContext.trim().toUpperCase(Locale.ROOT);
      if (!COUNTRY_CODE.matcher(region).matches()) {
        throw ContactCoreException.invalidIdentity();
      }
    }

    try {
      var parsed = phoneNumberUtil.parse(display, region);
      if (!phoneNumberUtil.isPossibleNumber(parsed) || !phoneNumberUtil.isValidNumber(parsed)) {
        throw ContactCoreException.invalidIdentity();
      }
      String e164 = phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
      return new NormalizedIdentity(IdentityType.PHONE, null, display, e164);
    } catch (NumberParseException exception) {
      throw ContactCoreException.invalidIdentity();
    }
  }

  private NormalizedIdentity normalizeExternal(String namespace, String value) {
    String normalizedNamespace =
        namespace == null
            ? null
            : Normalizer.normalize(namespace.trim(), Normalizer.Form.NFKC);
    String display = normalizeDisplay(value);
    if (normalizedNamespace == null
        || normalizedNamespace.isBlank()
        || normalizedNamespace.length() > MAX_NAMESPACE_LENGTH
        || display.isBlank()
        || display.length() > MAX_IDENTITY_LENGTH
        || containsControl(display)
        || containsControl(normalizedNamespace)) {
      throw ContactCoreException.invalidIdentity();
    }
    // EXTERNAL values are opaque in core. Provider adapters own any stronger canonicalization.
    return new NormalizedIdentity(
        IdentityType.EXTERNAL, normalizedNamespace, display, display);
  }

  private String normalizeDisplay(String value) {
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    if (normalized.isBlank()) {
      throw ContactCoreException.invalidIdentity();
    }
    return normalized;
  }

  private void requireNoNamespace(String namespace) {
    if (namespace != null && !namespace.isBlank()) {
      throw ContactCoreException.invalidIdentity();
    }
  }

  private boolean validDomain(String domain) {
    if (domain.isBlank() || domain.length() > 253 || domain.startsWith(".") || domain.endsWith(".")) {
      return false;
    }
    String[] labels = domain.split("\\.", -1);
    if (labels.length < 2) {
      return false;
    }
    for (String label : labels) {
      if (!DOMAIN_LABEL.matcher(label).matches()) {
        return false;
      }
    }
    return true;
  }

  private boolean containsControlOrWhitespace(String value) {
    return value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || Character.isWhitespace(cp));
  }

  private boolean containsControl(String value) {
    return value.codePoints().anyMatch(Character::isISOControl);
  }

  public record NormalizedIdentity(
      IdentityType type, String namespace, String displayValue, String normalizedValue) {}
}
