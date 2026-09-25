package com.messaging.emailprovider.validation.worker;

import com.messaging.emailprovider.validation.service.ValidationJobService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app",
    name = {"kafka.enabled", "validation.enabled"},
    havingValue = "true")
public class ValidationRetryScheduler {

  private final ValidationJobService jobs;

  public ValidationRetryScheduler(ValidationJobService jobs) {
    this.jobs = jobs;
  }

  @Scheduled(fixedDelayString = "${app.validation.retry.poll-interval-ms:5000}")
  public void retry() {
    jobs.requeueDueRetries(100);
  }
}
