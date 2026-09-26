package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMetaAudienceRequest(
    @NotBlank @Size(max=255) String name,
    @NotBlank String type,
    String description,
    String pixelId,
    String ruleJson,
    @Min(1) @Max(180) Integer retentionDays,
    String originAudienceId,
    String country,
    Double ratio,
    String customerFileSource) {
  @AssertTrue(message="Audience configuration does not match the selected type")
  public boolean validTypeConfiguration() {
    if (type == null) return false;
    String normalized = type.trim().toUpperCase();
    if ("WEBSITE".equals(normalized)) {
      return pixelId != null && !pixelId.isBlank() && ruleJson != null && !ruleJson.isBlank();
    }
    if ("LOOKALIKE".equals(normalized)) {
      return originAudienceId != null && !originAudienceId.isBlank()
          && country != null && !country.isBlank()
          && ratio != null && ratio >= 0.01d && ratio <= 0.20d;
    }
    if ("CUSTOMER_LIST".equals(normalized)) {
      return customerFileSource != null
          && ("USER_PROVIDED_ONLY".equals(customerFileSource.trim().toUpperCase())
              || "PARTNER_PROVIDED_ONLY".equals(customerFileSource.trim().toUpperCase())
              || "BOTH_USER_AND_PARTNER_PROVIDED".equals(
                  customerFileSource.trim().toUpperCase()));
    }
    return false;
  }
}
