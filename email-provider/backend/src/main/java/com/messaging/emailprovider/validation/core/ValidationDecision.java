package com.messaging.emailprovider.validation.core;

import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;
import com.messaging.emailprovider.validation.enums.ValidationReason;
import com.messaging.emailprovider.validation.enums.ValidationStatus;

public record ValidationDecision(
    ValidationStatus status,
    ValidationReason reason,
    int score,
    boolean domainRoutable,
    boolean mxFound,
    boolean disposable,
    boolean roleAddress,
    Boolean catchAll,
    SmtpProbeStatus smtpStatus,
    Integer smtpCode,
    String diagnostic) {}
