package com.messaging.billing.dto;

import com.messaging.common.validation.ValidPhone;

public record CompanyBillingProfileDto(
    String legalBusinessName,
    String taxIdOrGstin,
    String billingEmail,
    @ValidPhone String billingPhone,
    String addressLine1,
    String addressLine2,
    String city,
    String state,
    String postalCode,
    String country) {}
