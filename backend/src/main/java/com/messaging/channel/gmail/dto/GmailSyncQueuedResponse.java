package com.messaging.channel.gmail.dto;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import java.time.LocalDate;

public record GmailSyncQueuedResponse(
    String connectionId,
    String jobId,
    String status,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {}
