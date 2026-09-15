package com.messaging.kafka.event;

public record OtpRequestedEvent(
    String identifier, String deliveryChannel, String subject, String body, boolean html) {}
