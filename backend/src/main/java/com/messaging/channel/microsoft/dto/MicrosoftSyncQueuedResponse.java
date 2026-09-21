package com.messaging.channel.microsoft.dto;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import java.time.LocalDate;

public record MicrosoftSyncQueuedResponse(
    String connectionId,
    String jobId,
    String status,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {}
