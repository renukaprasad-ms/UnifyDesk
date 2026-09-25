package com.messaging.channel.microsoft.dto;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.email.EmailSyncQueuedResult;
import java.time.LocalDate;

public record MicrosoftSyncQueuedResponse(
    String connectionId,
    String jobId,
    String status,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {

  public static MicrosoftSyncQueuedResponse from(EmailSyncQueuedResult result) {
    return new MicrosoftSyncQueuedResponse(
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
