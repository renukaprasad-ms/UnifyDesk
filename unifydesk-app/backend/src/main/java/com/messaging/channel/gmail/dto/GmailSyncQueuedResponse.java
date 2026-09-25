package com.messaging.channel.gmail.dto;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.email.EmailSyncQueuedResult;
import java.time.LocalDate;

public record GmailSyncQueuedResponse(
    String connectionId,
    String jobId,
    String status,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {

  public static GmailSyncQueuedResponse from(EmailSyncQueuedResult result) {
    return new GmailSyncQueuedResponse(
        result.connectionId(),
        result.jobId(),
        result.status(),
        result.mode(),
        result.folder(),
        result.fromDate(),
        result.toDate(),
        result.messageCount());
  }
}
