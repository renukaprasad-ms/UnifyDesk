package com.messaging.emailprovider.reputation.dto;

import com.messaging.emailprovider.reputation.ReputationDaily;
import java.time.LocalDate;

public record ReputationDailyResponse(
    String sendingDomainId,
    LocalDate date,
    long accepted,
    long delivered,
    long bounced,
    long deferred,
    long complaints,
    double bounceRate,
    double complaintRate) {

  public static ReputationDailyResponse from(ReputationDaily row) {
    return new ReputationDailyResponse(
        row.getSendingDomainId().toString(),
        row.getMetricDate(),
        row.getAcceptedCount(),
        row.getDeliveredCount(),
        row.getBouncedCount(),
        row.getDeferredCount(),
        row.getComplaintCount(),
        rate(row.getBouncedCount(), row.getAcceptedCount()),
        rate(row.getComplaintCount(), row.getAcceptedCount()));
  }

  private static double rate(long numerator, long denominator) {
    if (denominator <= 0) {
      return 0.0;
    }
    return (double) numerator / denominator;
  }
}
