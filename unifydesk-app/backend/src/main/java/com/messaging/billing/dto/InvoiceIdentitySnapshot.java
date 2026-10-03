package com.messaging.billing.dto;

import jakarta.validation.constraints.Size;

public record InvoiceIdentitySnapshot(
    @Size(max = 160) String companyName,
    @Size(max = 254) String businessEmail,
    @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2,
    @Size(max = 120) String city,
    @Size(max = 120) String state,
    @Size(max = 32) String postalCode,
    @Size(max = 80) String country,
    @Size(max = 120) String taxId,
    @Size(max = 120) String taxRegistration) {}
