package com.messaging.emailprovider.domain.service;

import com.messaging.emailprovider.common.exception.BadRequestException;
import java.net.IDN;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DomainNameNormalizer {

  public String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Domain is required");
    }

    String candidate = value.trim().toLowerCase(Locale.ROOT);
    if (candidate.endsWith(".")) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }

    if (candidate.contains("://")
        || candidate.contains("/")
        || candidate.contains("@")
        || candidate.contains("*")
        || candidate.contains(" ")) {
      throw new BadRequestException("Invalid domain");
    }

    final String ascii;
    try {
      ascii = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid domain");
    }

    if (ascii.length() > 253 || !ascii.contains(".")) {
      throw new BadRequestException("A valid registrable domain is required");
    }

    String[] labels = ascii.split("\\.");
    for (String label : labels) {
      if (label.isBlank() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
        throw new BadRequestException("Invalid domain");
      }
    }
    return ascii;
  }
}
