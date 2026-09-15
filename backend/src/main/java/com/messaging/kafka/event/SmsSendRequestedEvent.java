package com.messaging.kafka.event;

public record SmsSendRequestedEvent(Long companyId, String phoneNumber, String messageRef) {}
