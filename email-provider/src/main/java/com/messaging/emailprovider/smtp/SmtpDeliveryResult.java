package com.messaging.emailprovider.smtp;

public record SmtpDeliveryResult(
    SmtpDeliveryOutcome outcome, String statusCode, String response) {}
