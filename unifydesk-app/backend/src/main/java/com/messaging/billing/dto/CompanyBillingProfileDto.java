package com.messaging.billing.dto;

public record CompanyBillingProfileDto(
    String legalBusinessName,
    String taxIdOrGstin,
    String billingEmail,
    String billingPhone,
    String addressLine1,
    String addressLine2,
    String city,
    String state,
    String postalCode,
    String country) {}
