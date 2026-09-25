package com.messaging.channel.email;

import java.time.LocalDate;

public record EmailSyncQueuedResult(
    String connectionId,
    String jobId,
    String status,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {}
