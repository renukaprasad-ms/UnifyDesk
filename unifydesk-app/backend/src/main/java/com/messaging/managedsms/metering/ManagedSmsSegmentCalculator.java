package com.messaging.managedsms.metering;

import org.springframework.stereotype.Component;

/**
 * Deterministic pre-send SMS segmentation estimate.
 *
 * <p>GSM 03.38 extension-table characters consume two septets. Content outside the GSM default and
 * extension alphabets is treated as Unicode and counted in UTF-16 code units, matching the
 * practical UCS-2/UTF-16 multipart accounting used by SMS providers. Provider billing truth may
 * still differ and must be reconciled immutably when an authoritative MSG91 source is available.
 */
@Component
public class ManagedSmsSegmentCalculator {

  public int segments(String body) {
    if (body == null || body.isEmpty()) return 0;

    int septets = gsmSeptets(body);
    if (septets >= 0) {
      if (septets <= 160) return 1;
      return (septets + 152) / 153;
    }

    int units = body.length();
    if (units <= 70) return 1;
    return (units + 66) / 67;
  }

  private int gsmSeptets(String body) {
    int total = 0;
    for (int offset = 0; offset < body.length(); ) {
      int value = body.codePointAt(offset);
      offset += Character.charCount(value);
      if (isGsmBasic(value)) {
        total++;
      } else if (isGsmExtended(value)) {
        total += 2;
      } else {
        return -1;
      }
    }
    return total;
  }

  private boolean isGsmBasic(int value) {
    return switch (value) {
      case '@',
          '£',
          '$',
          '¥',
          'è',
          'é',
          'ù',
          'ì',
          'ò',
          'Ç',
          '\n',
          'Ø',
          'ø',
          '\r',
          'Å',
          'å',
          'Δ',
          '_',
          'Φ',
          'Γ',
          'Λ',
          'Ω',
          'Π',
          'Ψ',
          'Σ',
          'Θ',
          'Ξ',
          'Æ',
          'æ',
          'ß',
          'É',
          '¤',
          '¡',
          'Ä',
          'Ö',
          'Ñ',
          'Ü',
          '§',
          '¿',
          'ä',
          'ö',
          'ñ',
          'ü',
          'à' ->
          true;
      default ->
          (value >= ' ' && value <= '?')
              || (value >= 'A' && value <= 'Z')
              || (value >= 'a' && value <= 'z');
    };
  }

  private boolean isGsmExtended(int value) {
    return value == '\f'
        || value == '^'
        || value == '{'
        || value == '}'
        || value == '\\'
        || value == '['
        || value == '~'
        || value == ']'
        || value == '|'
        || value == '€';
  }
}
