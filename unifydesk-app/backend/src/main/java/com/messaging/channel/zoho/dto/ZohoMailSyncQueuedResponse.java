package com.messaging.channel.zoho.dto;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.email.EmailSyncQueuedResult;
import java.time.LocalDate;

public record ZohoMailSyncQueuedResponse(
    String connectionId,
    String jobId,
    String status,
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    Integer messageCount) {

  public static ZohoMailSyncQueuedResponse from(EmailSyncQueuedResult result) {
    return new ZohoMailSyncQueuedResponse(
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
