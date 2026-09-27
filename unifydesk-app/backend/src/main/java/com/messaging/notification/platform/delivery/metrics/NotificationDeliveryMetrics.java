package com.messaging.notification.platform.delivery.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryMetrics {

  private final MeterRegistry meterRegistry;

  public void attempt(String channel, String outcome) {
    meterRegistry
        .counter(
            "notification_delivery_attempts_total",
            "channel",
            safe(channel),
            "outcome",
            safe(outcome))
        .increment();
  }

  public void emailUnknown() {
    meterRegistry.counter("notification_email_unknown_total").increment();
  }

  public void digestSent(String type) {
    meterRegistry.counter("notification_digest_sent_total", "type", safe(type)).increment();
  }

  public void digestSuppressed(String reason) {
    meterRegistry
        .counter("notification_digest_suppressed_total", "reason", safe(reason))
        .increment();
  }

  public void pushAttempt(String outcome, Integer status) {
    meterRegistry
        .counter(
            "notification_push_attempts_total",
            "outcome",
            safe(outcome),
            "http_status_class",
            statusClass(status))
        .increment();
  }

  private String statusClass(Integer status) {
    return status == null ? "none" : (status / 100) + "xx";
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "none" : value;
  }
}
