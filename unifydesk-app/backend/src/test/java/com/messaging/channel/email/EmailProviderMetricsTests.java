package com.messaging.channel.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.channel.enums.ProviderType;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EmailProviderMetricsTests {

  @Test
  void recordsSuccessAndFailureWithoutHighCardinalityTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    EmailProviderMetrics metrics = new EmailProviderMetrics(registry);

    assertThat(
            metrics.record(
                ProviderType.GMAIL,
                EmailProviderOperation.SEND,
                () -> "ok"))
        .isEqualTo("ok");

    assertThatThrownBy(
            () ->
                metrics.record(
                    ProviderType.GMAIL,
                    EmailProviderOperation.SEND,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    Timer success =
        registry
            .get("messaging.email.provider.operation")
            .tag("provider", "GMAIL")
            .tag("operation", "send")
            .tag("outcome", "success")
            .timer();
    Timer failure =
        registry
            .get("messaging.email.provider.operation")
            .tag("provider", "GMAIL")
            .tag("operation", "send")
            .tag("outcome", "failure")
            .timer();

    assertThat(success.count()).isEqualTo(1);
    assertThat(failure.count()).isEqualTo(1);
  }
}
