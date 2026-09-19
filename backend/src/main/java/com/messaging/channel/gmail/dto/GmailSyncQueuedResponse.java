package com.messaging.channel.gmail.dto;

import java.time.LocalDate;

public record GmailSyncQueuedResponse(
    String connectionId, String jobId, String status, LocalDate fromDate, LocalDate toDate) {}
