package com.messaging.broadcast.sendgrid;

import com.messaging.common.exception.BadRequestException;

public enum SendGridRegion {
  GLOBAL("https://api.sendgrid.com"),
  EU("https://api.eu.sendgrid.com");

  private final String baseUrl;

  SendGridRegion(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String baseUrl() {
    return baseUrl;
  }

  public static SendGridRegion fromStoredValue(String value) {
    if (value == null || value.isBlank()) return GLOBAL;
    try {
      return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Unsupported SendGrid region: " + value);
    }
  }
}
