package com.messaging.emailprovider.validation.smtp;

import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;

public record SmtpProbeResult(SmtpProbeStatus status, Integer code, String diagnostic) {}
