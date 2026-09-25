package com.messaging.channel.email;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record EmailSyncRequest(
    EmailSyncMode mode,
    EmailFolder folder,
    LocalDate fromDate,
    LocalDate toDate,
    @Min(1) @Max(10000) Integer messageCount) {

  public EmailSyncRequest {
    if (mode == null) mode = EmailSyncMode.DATE_RANGE;
    if (folder == null) folder = EmailFolder.INBOX;
  }
}
