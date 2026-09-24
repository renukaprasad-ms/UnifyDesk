package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GoogleCustomerMatchUploadRequest(
    @NotEmpty List<@Valid GoogleCustomerMatchMember> members,
    boolean adUserDataConsent,
    boolean adPersonalizationConsent,
    boolean customerMatchTermsAccepted,
    boolean validateOnly) {}
