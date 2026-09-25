package com.messaging.kafka.event;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import java.time.LocalDate;

public record GmailSyncRequestedEvent(
    Long connectionId,
    Long jobId,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {}
