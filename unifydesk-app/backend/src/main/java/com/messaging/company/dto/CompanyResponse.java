package com.messaging.company.dto;

import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.CompanyStatus;

public record CompanyResponse(
    String companyId,
    String name,
    String slug,
    String businessEmail,
    String country,
    String timezone,
    String logoMediaId,
    CompanyStatus status,
    CompanyRole role,
    String brandColor) {

  public CompanyResponse(
      String companyId,
      String name,
      String slug,
      String businessEmail,
      String country,
      String timezone,
      String logoMediaId,
      CompanyStatus status,
      CompanyRole role) {
    this(
        companyId,
        name,
        slug,
        businessEmail,
        country,
        timezone,
        logoMediaId,
        status,
        role,
        "#2563EB");
  }
}
