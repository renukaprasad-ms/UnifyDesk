package com.messaging.channel.email;

import com.messaging.common.exception.BadRequestException;
import java.time.temporal.ChronoUnit;

public final class EmailSyncRequestValidator {

  private EmailSyncRequestValidator() {}

  public static void validate(
      EmailSyncRequest request, int maxRangeDays, int maxMessages) {
    if (request == null || request.mode() == null || request.folder() == null) {
      throw new BadRequestException("Email sync mode and folder are required");
    }
    if (request.mode() == EmailSyncMode.REALTIME_RECOVERY || request.folder() == EmailFolder.ALL) {
      throw new BadRequestException("Internal email recovery mode is not available through this API");
    }

    if (request.mode() == EmailSyncMode.LATEST_MESSAGES) {
      if (request.messageCount() == null || request.messageCount() < 1) {
        throw new BadRequestException("messageCount is required for latest-message sync");
      }
      if (request.messageCount() > Math.max(1, maxMessages)) {
        throw new BadRequestException(
            "messageCount cannot exceed " + Math.max(1, maxMessages));
      }
      return;
    }

    if (request.fromDate() == null || request.toDate() == null) {
      throw new BadRequestException("fromDate and toDate are required for date-range sync");
    }
    if (request.fromDate().isAfter(request.toDate())) {
      throw new BadRequestException("fromDate must be on or before toDate");
    }

    long days = ChronoUnit.DAYS.between(request.fromDate(), request.toDate());
    if (days > Math.max(1, maxRangeDays)) {
      throw new BadRequestException(
          "Email sync range cannot exceed " + Math.max(1, maxRangeDays) + " days");
    }
  }
}
