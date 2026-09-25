package com.messaging.channel.email;

import com.messaging.channel.enums.ProviderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class EmailProviderMetrics {

  private static final String METRIC_NAME = "messaging.email.provider.operation";

  private final MeterRegistry meterRegistry;
  private final Map<ProviderType, Map<EmailProviderOperation, OutcomeTimers>> timers;

  public EmailProviderMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.timers = createTimers();
  }

  public <T> T record(
      ProviderType providerType, EmailProviderOperation operation, Supplier<T> action) {
    Timer.Sample sample = Timer.start(meterRegistry);
    boolean success = false;
    try {
      T result = action.get();
      success = true;
      return result;
    } finally {
      OutcomeTimers operationTimers = timers.get(providerType).get(operation);
      sample.stop(success ? operationTimers.success() : operationTimers.failure());
    }
  }

  public void record(
      ProviderType providerType, EmailProviderOperation operation, Runnable action) {
    record(
        providerType,
        operation,
        () -> {
          action.run();
          return null;
        });
  }

  private Map<ProviderType, Map<EmailProviderOperation, OutcomeTimers>> createTimers() {
    EnumMap<ProviderType, Map<EmailProviderOperation, OutcomeTimers>> byProvider =
        new EnumMap<>(ProviderType.class);

    for (ProviderType providerType : ProviderType.values()) {
      EnumMap<EmailProviderOperation, OutcomeTimers> byOperation =
          new EnumMap<>(EmailProviderOperation.class);
      for (EmailProviderOperation operation : EmailProviderOperation.values()) {
        byOperation.put(
            operation,
            new OutcomeTimers(
                timer(providerType, operation, "success"),
                timer(providerType, operation, "failure")));
      }
      byProvider.put(providerType, Map.copyOf(byOperation));
    }
    return Map.copyOf(byProvider);
  }

  private Timer timer(
      ProviderType providerType, EmailProviderOperation operation, String outcome) {
    return Timer.builder(METRIC_NAME)
        .description("Email provider operation latency")
        .tag("provider", providerType.name())
        .tag("operation", operation.metricTag())
        .tag("outcome", outcome)
        .register(meterRegistry);
  }

  private record OutcomeTimers(Timer success, Timer failure) {}
}
