package com.messaging.kafka.event;

import java.time.LocalDate;

public record GmailSyncRequestedEvent(
    Long connectionId, Long jobId, LocalDate fromDate, LocalDate toDate) {}
