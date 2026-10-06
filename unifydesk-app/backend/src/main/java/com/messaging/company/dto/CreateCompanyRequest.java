package com.messaging.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
    @NotBlank @Size(max = 160) String name,
    @Email @Size(max = 254) String businessEmail,
    @Size(max = 80) String country,
    @Size(max = 80) String timezone,
    String logoMediaId,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String brandColor) {

  public CreateCompanyRequest(
      String name, String businessEmail, String country, String timezone, String logoMediaId) {
    this(name, businessEmail, country, timezone, logoMediaId, "#2563EB");
  }
}
