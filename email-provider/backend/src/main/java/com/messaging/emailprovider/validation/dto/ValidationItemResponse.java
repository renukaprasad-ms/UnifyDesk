package com.messaging.emailprovider.validation.dto;

import com.messaging.emailprovider.validation.entity.EmailValidationItem;
import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;
import com.messaging.emailprovider.validation.enums.ValidationReason;
import com.messaging.emailprovider.validation.enums.ValidationStatus;
import java.time.Instant;

public record ValidationItemResponse(
    String id,
    String email,
    String normalizedEmail,
    ValidationStatus status,
    ValidationReason reason,
    Integer score,
    Boolean syntaxValid,
    Boolean domainRoutable,
    Boolean mxFound,
    Boolean disposable,
    Boolean roleAddress,
    Boolean catchAll,
    SmtpProbeStatus smtpStatus,
    Integer smtpCode,
    String diagnostic,
    Instant validatedAt) {

  public static ValidationItemResponse from(EmailValidationItem item) {
    return new ValidationItemResponse(
        item.getId().toString(),
        item.getOriginalEmail(),
        item.getNormalizedEmail(),
        item.getStatus(),
        item.getReason(),
        item.getScore(),
        item.getSyntaxValid(),
        item.getDomainRoutable(),
        item.getMxFound(),
        item.getDisposable(),
        item.getRoleAddress(),
        item.getCatchAll(),
        item.getSmtpStatus(),
        item.getSmtpCode(),
        item.getDiagnostic(),
        item.getValidatedAt());
  }
}
